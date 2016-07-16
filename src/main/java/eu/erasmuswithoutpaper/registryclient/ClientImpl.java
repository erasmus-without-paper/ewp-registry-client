package eu.erasmuswithoutpaper.registryclient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import eu.erasmuswithoutpaper.registryclient.CatalogueDocument.CatalogueParserException;
import eu.erasmuswithoutpaper.registryclient.CatalogueFetcher.Http200RegistryResponse;
import eu.erasmuswithoutpaper.registryclient.CatalogueFetcher.Http304RegistryResponse;
import eu.erasmuswithoutpaper.registryclient.CatalogueFetcher.RegistryResponse;
import eu.erasmuswithoutpaper.registryclient.CatalogueFetcher.Http200RegistryResponse.CouldNotDeserialize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

/**
 * A thread-safe {@link RegistryClient} implementation with internal catalogue copy and background
 * synchronization options.
 *
 * <p>
 * {@link ClientImpl} keeps a copy of the Registry's catalogue in memory and allows all queries to
 * be performed fast, without the need to query the remote server. This copy can be refreshed both
 * manually or automatically (see {@link ClientImplOptions#setAutoRefreshing(boolean)} for details).
 * </p>
 *
 * <h3>Usage as an {@literal @}Autowired Spring bean</h3>
 *
 * <p>
 * If you're using Spring, then the you can use this client as a singleton bean. It will cause a
 * copy of the Registry's catalogue to reside in your memory for fast access whenever needed:
 * </p>
 *
 * <pre>
 * {@literal @}Bean
 * public RegistryClient getEwpRegistryClient() {
 *   ClientImplOptions options = new ClientImplOptions();
 *   options.setAutoRefreshing(true);
 *   return new ClientImpl(options);
 * }
 * </pre>
 *
 * <p>
 * If you believe that you won't be using the client much (and you don't want to keep it in memory
 * all the time), then you can also use a different scope for your bean (e.g.
 * <code>{@literal @Scope("request")}</code>). In such cases however, it is recommended to supply
 * {@link ClientImplOptions#setPersistentCacheMap(Map)} (to speed up initialization).
 * </p>
 *
 * <p>
 * Note: Spring will automatically call close() on AutoClosable beans.
 * </p>
 *
 * <h3>Basic in-line usage</h3>
 *
 * <p>
 * When used in-line, remember to wrap it in try-with-resources (or, remember to call
 * {@link #close()} manually). If you will be creating new instances often, then it is recommended
 * to supply {@link ClientImplOptions#setPersistentCacheMap(Map)} in your <code>options</code>:
 * </p>
 *
 * <pre>
 * ClientImplOptions options = new ClientImplOptions();
 * options.setAutoRefreshing(true);
 * options.setPersistentCacheMap(...); // e.g. memcached server
 * try (RegistryClient client = new ClientImpl(options)) {
 *   // Your queries.
 * }
 * </pre>
 */
public class ClientImpl implements RegistryClient {

  private static final String CATALOGUE_CACHE_KEY = "latest-catalogue";
  private static final Logger logger = LoggerFactory.getLogger(ClientImpl.class);

  /**
   * {@link ClientImplOptions} which we've been constructed with.
   */
  private final ClientImplOptions options;

  /**
   * The instance of the {@link CatalogueDocument} which we are currently operating on.
   *
   * <p>
   * This is not final, because a new instance of the {@link CatalogueDocument} is created whenever
   * the content of the catalogue changes (see {@link ClientImplOptions#getAutoRefreshing()}).
   * </p>
   */
  private volatile CatalogueDocument doc;

  /**
   * This will be used if {@link ClientImplOptions#getAutoRefreshing()} is true. Otherwise, it will
   * be null.
   */
  private final ScheduledExecutorService executor;

  /**
   * Construct {@link ClientImpl} with default {@link ClientImplOptions}.
   */
  public ClientImpl() {
    this(new ClientImplOptions());
  }

  /**
   * Construct {@link ClientImpl}.
   *
   * @param options Options to use. These options cannot be changed after the constructor is called
   *        (if they do, then behavior will be undetermined).
   */
  public ClientImpl(ClientImplOptions options) {

    this.options = options;
    logger.info("Constructing new ClientImpl with options: " + options);

    /*
     * If we are provided with a persistent cache, then will try to load a copy of the catalogue
     * directly from it.
     */

    Map<String, byte[]> cache = this.options.getPersistentCacheProvider();
    if (cache != null) {
      logger.debug("Attempting to load a catalogue from cache");
      byte[] data = cache.get(CATALOGUE_CACHE_KEY);
      if (data != null) {
        try {
          Http200RegistryResponse cachedResponse =
              Http200RegistryResponse.deserialize(cache.get(CATALOGUE_CACHE_KEY));
          this.doc = new CatalogueDocument(cachedResponse);
          logger.info("Loaded a catalogue from cache: " + this.doc);
        } catch (CatalogueParserException | CouldNotDeserialize e) {
          logger.debug("Could not load the catalogue from cache: " + e);
        }
      }
    }

    // If no cache was provided, or loading failed, then use an empty placeholder.

    if (this.doc == null) {
      StringBuilder sb = new StringBuilder();
      sb.append("<catalogue xmlns='");
      sb.append(RegistryClient.REGISTRY_CATALOGUE_V1_NAMESPACE_URI);
      sb.append("'></catalogue>");
      byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
      String newETag = "empty-placeholder";
      Date expires = new Date(0);
      Http200RegistryResponse emptyResponse =
          new Http200RegistryResponse(content, newETag, expires);
      try {
        this.doc = new CatalogueDocument(emptyResponse);
      } catch (CatalogueParserException e) {
        throw new RuntimeException(e);
      }
    }

    if (options.getAutoRefreshing()) {

      /*
       * If our current copy of the catalogue has expired, then we will attempt to refresh it during
       * the construction time. This will cause the object to be constructed much slower, but it
       * seems safer, as the caller might want to use the client right after the call.
       */

      if (this.getExpiryDate().after(new Date())) {
        logger.debug("The cached copy seems to be acceptable. "
            + "We won't be refreshing it (this will speed up the construction).");
      } else {
        logger.debug("The current-held copy of the catalogue is expired. "
            + "We will refresh it now (this might make construction a bit slower).");
        try {
          this.refresh();
        } catch (RefreshFailureException e2) {
          this.logRefreshFailure(
              "Failed to fetch a fresh copy of the catalogue during construction "
                  + "(we will keep trying in a background thread).",
              e2);
        }
      }

      /*
       * Set up a periodical catalogue refresh task. THIS SHOULD BE THE LAST SECTION (because we're
       * starting new threads here).
       */

      this.executor = Executors.newSingleThreadScheduledExecutor();
      this.executor.schedule(
          new SelfSchedulableTask(this.executor, this.options.getMinTimeBetweenQueries()) {

            @Override
            protected Date runAndScheduleNext() {
              try {
                logger.trace("runAndScheduleNext was called");
                Date now = new Date();
                Date expiryDate = ClientImpl.this.getExpiryDate();
                if (expiryDate.after(now)) {
                  logger.trace("No refresh was necessary. Will retry at " + expiryDate);
                  return ClientImpl.this.getExpiryDate();
                }
                ClientImpl.this.refresh();
                return ClientImpl.this.getExpiryDate();
              } catch (RefreshFailureException e) {
                ClientImpl.this
                    .logRefreshFailure("Scheduled catalogue refresh failed. Will retry in "
                        + ClientImpl.this.options.getTimeBetweenRetries() + "ms.", e);
                return new Date(
                    new Date().getTime() + ClientImpl.this.options.getTimeBetweenRetries());
              }
            }

          }, 0, TimeUnit.MILLISECONDS);

    } else {
      this.executor = null;
    }
  }

  @Override
  public boolean areHeisCoveredByCertificate(Collection<String> heiIds, Certificate clientCert)
      throws UnacceptableStalenessException {
    return this.getHeisCoveredByCertificate(clientCert).containsAll(heiIds);
  }

  @Override
  public boolean areHeisCoveredByCertificate(String[] heiIds, Certificate clientCert)
      throws UnacceptableStalenessException {
    Collection<String> heis = this.getHeisCoveredByCertificate(clientCert);
    for (String heiId : heiIds) {
      if (!heis.contains(heiId)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void assertCertificateIsKnown(Certificate clientCert) throws AssertionFailedException {
    if (!this.isCertificateKnown(clientCert)) {
      throw new AssertionFailedException(
          "Certificate was not recognized as a known EWP Client: " + clientCert.toString());
    }
  }

  @Override
  public void assertHeiIsCoveredByCertificate(String heiId, Certificate clientCert)
      throws AssertionFailedException {
    if (!this.isHeiCoveredByCertificate(heiId, clientCert)) {
      throw new AssertionFailedException("HEI " + heiId + " is not covered by this certificate.");
    }
  }

  @Override
  public void assertHeisAreCoveredByCertificate(Collection<String> heiIds, Certificate clientCert)
      throws AssertionFailedException {
    if (!this.areHeisCoveredByCertificate(heiIds, clientCert)) {
      throw new AssertionFailedException("Some of the HEIs are not covered by this certificate.");
    }
  }

  @Override
  public void assertHeisAreCoveredByCertificate(String[] heiIds, Certificate clientCert)
      throws AssertionFailedException {
    if (!this.areHeisCoveredByCertificate(heiIds, clientCert)) {
      throw new AssertionFailedException("Some of the HEIs are not covered by this certificate.");
    }
  }

  @Override
  public void close() {
    logger.info("ClientImpl is closing");
    if (this.executor != null) {
      this.executor.shutdownNow();
      try {
        if (this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
          logger.info("All threads exited successfully.");
        } else {
          logger.warn("Some threads are still running, but we won't wait anymore.");
        }
      } catch (InterruptedException e) {
        logger.warn("Interrupted while waiting for threads to finish.");
      }
    }
    logger.info("ClientImpl finished closing");
  }

  @Override
  public Element findApi(ApiSearchConditions conditions) {
    // Since expiry date can only be extended, there is no need to synchronize.
    this.assertAcceptableStaleness();
    return this.doc.findApi(conditions);
  }

  @Override
  public Collection<Element> findApis(ApiSearchConditions conditions) {
    // Since expiry date can only be extended, there is no need to synchronize.
    this.assertAcceptableStaleness();
    return this.doc.findApis(conditions);
  }

  @Override
  public String findHeiId(String type, String value) {
    // Since expiry date can only be extended, there is no need to synchronize.
    this.assertAcceptableStaleness();
    return this.doc.findHeiId(type, value);
  }

  @Override
  public Date getExpiryDate() {
    // No need to synchronize. Simply get the expiry date of the currently held doc.
    return this.doc.getExpiryDate();
  }

  @Override
  public Collection<String> getHeisCoveredByCertificate(Certificate clientCert) {
    // Since expiry date can only be extended, there is no need to synchronize.
    this.assertAcceptableStaleness();
    return this.doc.getHeisCoveredByCertificate(clientCert);
  }

  @Override
  public boolean isCertificateKnown(Certificate clientCert) {
    // Since expiry date can only be extended, there is no need to synchronize.
    this.assertAcceptableStaleness();
    return this.doc.isCertificateKnown(clientCert);
  }

  @Override
  public boolean isHeiCoveredByCertificate(String heiId, Certificate clientCert) {
    // Since expiry date can only be extended, there is no need to synchronize.
    this.assertAcceptableStaleness();
    return this.areHeisCoveredByCertificate(new String[] { heiId }, clientCert);
  }

  @Override
  public void refresh() throws RefreshFailureException {
    logger.trace("Starting a new refresh call");

    // Fetch the new catalogue from server.

    CatalogueFetcher catalogueFetcher = this.options.getCatalogueFetcher();
    RegistryResponse someResponse;
    try {
      logger.trace("Fetching response from the catalogueFetcher");
      someResponse = catalogueFetcher.fetchCatalogue(this.doc.getETag());
      logger.trace("Response fetched successfully: " + someResponse.getClass());
    } catch (IOException e) {
      logger.debug("CatalogueFetcher has thrown an IOException", e);
      throw new RefreshFailureException("Problem fetching the catalogue from server", e);
    }

    // What kind of response did we receive?

    if (someResponse instanceof Http304RegistryResponse) {

      /*
       * Catalogue did not change since the previous call. This means that we already have the
       * current version of the catalogue already parsed in our fields.
       */

      logger.info("Extending the expiry date of our catalogue copy: " + someResponse.getExpires());
      this.doc.extendExpiryDate(someResponse.getExpires());

      Map<String, byte[]> cache = this.options.getPersistentCacheProvider();
      if (cache != null) {
        logger.trace("Trying to extend the expiry date of the cached copy too...");
        byte[] data = cache.get(CATALOGUE_CACHE_KEY);
        if (data != null) {
          try {
            Http200RegistryResponse oldCachedResponse = Http200RegistryResponse.deserialize(data);
            Http200RegistryResponse newCachedResponse =
                new Http200RegistryResponse(oldCachedResponse.getContent(),
                    oldCachedResponse.getETag(), this.doc.getExpiryDate());
            cache.put(CATALOGUE_CACHE_KEY, newCachedResponse.serialize());
            logger.trace("Successfully updated");
          } catch (CouldNotDeserialize e) {
            logger.info("Could not extend the expiry date of the cached copy", e);
          }
        } else {
          logger.debug("Cached copy not found");
        }
      }

      return;

    } else if (someResponse instanceof Http200RegistryResponse) {

      /*
       * Catalogue has changed. We will create a new document (along with all the indexes), and -
       * once we complete this - start using it. (In the meantime, we will keep serving the previous
       * document.)
       */

      logger.trace("Preparing a new catalogue copy");
      Http200RegistryResponse response = (Http200RegistryResponse) someResponse;
      try {
        this.doc = new CatalogueDocument(response);
        logger.info("Catalogue copy successfully updated: " + this.doc);
      } catch (CatalogueParserException e) {
        logger.debug("Could not parse the new catalogue", e);
        throw new RefreshFailureException(e);
      }

      // Also store the new response in persistent cache (if we have one).

      Map<String, byte[]> cache = this.options.getPersistentCacheProvider();
      if (cache != null) {
        logger.trace("Storing the new copy to cache...");
        cache.put(CATALOGUE_CACHE_KEY, response.serialize());
      }

    } else {
      throw new RuntimeException(
          "CatalogueFetcher returned an unsupported RegistryResponse subclass: "
              + someResponse.getClass());
    }
  }

  /**
   * Make sure that the internal copy of the catalogue is acceptably fresh.
   *
   * @throws UnacceptableStalenessException if the age of catalogue exceeds
   *         {@link ClientImplOptions#getMaxAcceptableStaleness()}.
   */
  private void assertAcceptableStaleness() {
    Date acceptableUntil =
        new Date(this.getExpiryDate().getTime() + this.options.getMaxAcceptableStaleness());
    if (new Date().after(acceptableUntil)) {
      throw new UnacceptableStalenessException();
    }
  }

  /**
   * A helper method for logging {@link RegistryClient.RefreshFailureException} exceptions.
   *
   * <p>
   * It logs the message with different severity, depending on the current "level of staleness" (if
   * the cached copy becomes really stale, it will start to produce warnings).
   * </p>
   */
  private void logRefreshFailure(String message, RefreshFailureException ex) {
    long age = new Date().getTime() - this.getExpiryDate().getTime();
    if (age > this.options.getStalenessWarningThreshold()) {
      logger.warn(message + ": " + ex);
    } else {
      logger.info(message + ": " + ex);
    }
  }
}
