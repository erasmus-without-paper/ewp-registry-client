package eu.erasmuswithoutpaper.registryclient;

import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Date;

import org.w3c.dom.Element;

/**
 * Allows to perform basic queries to <a href='https://registry.erasmuswithoutpaper.eu/'>EWP
 * Registry Service</a>.
 *
 * <p>
 * This interface exists in order for developers to be able supply their own alternative
 * implementations (e.g. for testing purposes). Usually you will use {@link ClientImpl} as the
 * implementation of this interface. Remember to call {@link RegistryClient#close()} after you're
 * done with instances of this interface.
 * </p>
 *
 * @since 1.0.0
 */
public interface RegistryClient extends AutoCloseable {

  /**
   * Thrown whenever one of the {@link RegistryClient}'s <code>assert*</code> methods fails its
   * assertion.
   *
   * @since 1.0.0
   */
  @SuppressWarnings("serial")
  class AssertionFailedException extends RegistryClientException {

    public AssertionFailedException(String message) {
      super(message);
    }
  }

  /**
   * Thrown by {@link RegistryClient#refresh()} when the catalogue refreshing fails for some reason.
   *
   * @since 1.0.0
   */
  @SuppressWarnings({ "serial" })
  class RefreshFailureException extends RegistryClientException {

    public RefreshFailureException(Exception cause) {
      super(cause);
    }

    public RefreshFailureException(String message) {
      super(message);
    }

    public RefreshFailureException(String message, Exception cause) {
      super(message, cause);
    }
  }

  /**
   * A common base for all {@link RegistryClient} checked exceptions.
   *
   * @since 1.0.0
   */
  @SuppressWarnings({ "serial" })
  abstract class RegistryClientException extends Exception {

    protected RegistryClientException(Exception cause) {
      super(cause);
    }

    protected RegistryClientException(String message) {
      super(message);
    }

    protected RegistryClientException(String message, Exception cause) {
      super(message, cause);
    }
  }

  /**
   * A common base for all {@link RegistryClient} runtime exceptions.
   *
   * @since 1.0.0
   */
  @SuppressWarnings("serial")
  abstract class RegistryClientRuntimeException extends RuntimeException {
  }

  /**
   * Thrown by multiple {@link RegistryClient} methods when their internal copy of the Registry's
   * catalogue is "too old".
   *
   * <p>
   * Most {@link RegistryClient} implementations will keep an internal copy of the Registry's
   * catalogue in memory, in order to perform faster queries, and to counteract EWP network failure
   * due to a temporary EWP Registry Service downtime. If, for some reason, the internal copy of the
   * catalogue grows "too old", then this exception will be raised. (Note, that it can also be
   * caused by improper configuration of your server, not necessarily by the Registry Service's
   * downtime.)
   * </p>
   *
   * <p>
   * The exact definition of "too old" depends on particular implementation. For example,
   * {@link ClientImpl} allows you to set your own limit of staleness via
   * {@link ClientImplOptions#setMaxAcceptableStaleness(long)}. You can also check
   * {@link RegistryClient#getExpiryDate()} manually and make decisions based on its value.
   * </p>
   *
   * @since 1.0.0
   */
  @SuppressWarnings("serial")
  class UnacceptableStalenessException extends RegistryClientRuntimeException {
  }

  /**
   * Official Registry API catalogue's namespace URI.
   */
  String REGISTRY_CATALOGUE_V1_NAMESPACE_URI =
      "https://github.com/erasmus-without-paper/ewp-specs-api-registry/tree/stable-v1";

  /**
   * Check if given set of HEIs is completely covered the given certificate.
   *
   * <p>
   * In other words, check if each HEI on the list present on the list of HEIs covered by this
   * certificate (the list may still contain other HEIs).
   * </p>
   *
   * @param heiIds the list HEI
   *        <a href='https://github.com/erasmus-without-paper/ewp-specs-api-registry#schac-ids'>
   *        SCHAC ID</a>s that need to be covered.
   * @param clientCert as in {@link #isCertificateKnown(Certificate)}.
   * @return <b>true</b> if all HEIs are covered by this certificate, <b>false</b> if at least one
   *         of them isn't, or if the certificate is not known.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException} for more information.
   */
  boolean areHeisCoveredByCertificate(Collection<String> heiIds, Certificate clientCert)
      throws UnacceptableStalenessException;

  /**
   * This is an alias of {@link #areHeisCoveredByCertificate(Collection, Certificate)}. It just
   * takes <code>String[]</code> instead of a collection.
   *
   * @param heiIds an array of HEI SCHAC IDs that need to be covered.
   * @param clientCert as in {@link #isCertificateKnown(Certificate)}.
   * @return <b>true</b> if all HEIs are covered by this certificate, <b>false</b> if at least one
   *         of them isn't, or if the certificate is not known.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException} for more information.
   */
  boolean areHeisCoveredByCertificate(String[] heiIds, Certificate clientCert)
      throws UnacceptableStalenessException;

  /**
   * Performs the same action as described by {@link #isCertificateKnown(Certificate)}, but throws
   * an exception instead of returning booleans.
   *
   * @param clientCert as in {@link #isCertificateKnown(Certificate)}.
   * @throws AssertionFailedException if this certificate has not been listed in the Registry's
   *         catalogue.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException} for more information.
   */
  void assertCertificateIsKnown(Certificate clientCert)
      throws AssertionFailedException, UnacceptableStalenessException;

  /**
   * Performs the same action as described by
   * {@link #isHeiCoveredByCertificate(String, Certificate)}, but throws an exception instead of
   * returning booleans.
   *
   * @param heiId as in {@link #isHeiCoveredByCertificate(String, Certificate)}.
   * @param clientCert as in {@link #isHeiCoveredByCertificate(String, Certificate)}.
   * @throws AssertionFailedException if the HEI is not covered by this certificate, or the
   *         certificate is not known.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException} for more information.
   */
  void assertHeiIsCoveredByCertificate(String heiId, Certificate clientCert)
      throws AssertionFailedException, UnacceptableStalenessException;

  /**
   * Performs the same action as described by
   * {@link #areHeisCoveredByCertificate(Collection, Certificate)}, but throws an exception instead
   * of returning booleans.
   *
   * @param heiIds as in {@link #areHeisCoveredByCertificate(Collection, Certificate)}.
   * @param clientCert as in {@link #areHeisCoveredByCertificate(Collection, Certificate)}.
   * @throws AssertionFailedException if at least one of the HEIs is not covered by the certificate,
   *         or the certificate is not known.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException} for more information.
   */
  void assertHeisAreCoveredByCertificate(Collection<String> heiIds, Certificate clientCert)
      throws AssertionFailedException, UnacceptableStalenessException;

  /**
   * This is an alias of {@link #assertHeisAreCoveredByCertificate(Collection, Certificate)}. It
   * just takes <code>String[]</code> instead of a collection.
   *
   * @param heiIds as in {@link #areHeisCoveredByCertificate(String[], Certificate)}.
   * @param clientCert as in {@link #areHeisCoveredByCertificate(String[], Certificate)}.
   * @throws AssertionFailedException if at least one of the HEIs is not covered by the certificate,
   *         or the certificate is not known.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException} for more information.
   */
  void assertHeisAreCoveredByCertificate(String[] heiIds, Certificate clientCert)
      throws AssertionFailedException, UnacceptableStalenessException;

  /**
   * Relinquish all underlying resources.
   *
   * <p>
   * You should call this once you don't need the {@link RegistryClient} anymore. Note that this is
   * part of the {@link AutoCloseable} interface, so if you're using frameworks such as Spring then
   * this might be called automatically (provided that you're using {@link RegistryClient} as a
   * bean).
   * </p>
   */
  @Override
  void close();

  /**
   * Find particular API implementation in the network.
   *
   * <p>
   * If multiple matches are found, then this method will return the one that has the highest
   * version attribute. This is valid in most cases, but you can use the
   * {@link #findApis(ApiSearchConditions)} method if you want to retrieve the full list of matching
   * results.
   * </p>
   *
   * <p>
   * The exact format of this API entry depends on the API's class (the one you set via
   * {@link ApiSearchConditions#setApiClassRequired(String, String)}). In case of primary EWP APIs,
   * their API entries are described in <code>manifest-entry.xsd</code> files placed in along with
   * the API specs in GitHub. Keep in mind, that the Registry Service is <b>not required</b> to
   * validate the XSDs of all of the API entries it serves (especially if the API is not related to
   * the original EWP project). This means that you might want to validate this element yourself,
   * before using it.
   * </p>
   *
   * @param conditions Describes the conditions to search for.
   * @return An XML DOM {@link Element} with the API entry, exactly as the were served by the
   *         Registry Service.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException} for more information.
   */
  Element findApi(ApiSearchConditions conditions) throws UnacceptableStalenessException;

  /**
   * Find all API implementations matching the given conditions.
   *
   * <p>
   * This works the same as {@link #findApi(ApiSearchConditions)} does, but it returns a collection
   * of all matched API entry elements, instead of just "the best one".
   * </p>
   *
   * @param conditions as in {@link #findApi(ApiSearchConditions)}.
   * @return a collection of XML DOM {@link Element}s with API entries, exactly as they were served
   *         by the Registry Service. Please read the notes in {@link #findApi(ApiSearchConditions)}
   *         too.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException} for more information.
   */
  Collection<Element> findApis(ApiSearchConditions conditions)
      throws UnacceptableStalenessException;

  /**
   * Find HEI's SCHAC ID by providing other type of ID.
   *
   * <p>
   * Registry Service keeps a mapping of various popular HEI IDs and allows you to translate them to
   * SCHAC IDs used within the EWP Network. You can use this method, for example, to periodically
   * populate your database fields with SCHAC IDs.
   * </p>
   *
   * @param type This can be any string, but in most cases you will use <code>"pic"</code>,
   *        <code>"erasmus"</code> or <code>"previous-schac"</code> here. Check the <a href=
   *        'https://github.com/erasmus-without-paper/ewp-specs-api-registry/blob/stable-v1/catalogue.xsd'>
   *        current version</a> of the <code>catalogue.xsd</code> file in the Registry API
   *        specification for more identifiers.
   * @param value The searched value (e.g. if you have provided <code>"pic"</code> in <b>type</b>
   *        argument, then this should be the PIC code of the HEI being searched for). Note, that
   *        {@link RegistryClient} implementations are allowed to transform your input slightly
   *        (e.g. remove whitespace, or ignore the case) before the matching occurs.
   * @return Either String or <b>null</b>. String with a valid SCHAC ID of this HEI is returned, if
   *         a matching HEI was found. If no match was found, <b>null</b> is returned.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException} for more information.
   */
  String findHeiId(String type, String value) throws UnacceptableStalenessException;

  /**
   * Get the expiry date of the currently held copy of the catalogue.
   *
   * <p>
   * Most {@link RegistryClient} implementations will hold a copy of the Registry's catalogue in
   * memory between calls. Whenever a new copy of the catalogue is fetched, the expiry-date of this
   * copy should be fetched along with it. You can use this method to retrieve this expiry date.
   * </p>
   *
   * <p>
   * In general, this date should not be "too much" in the past. If it is, then most
   * {@link RegistryClient} implementations (such as {@link ClientImpl}) will start to throw
   * {@link UnacceptableStalenessException} exceptions when other {@link RegistryClient} methods are
   * called.
   * </p>
   *
   * @return The expiry date, as returned by the Registry Service when the currently held copy of
   *         the catalogue has been fetched.
   */
  Date getExpiryDate();

  /**
   * Retrieve a list of HEIs covered by the given certificate.
   *
   * <p>
   * Please note, that this list <b>will also be empty if the certificate is unknown</b>. Use
   * {@link #assertCertificateIsKnown(Certificate)} if you need to differentiate between these two
   * scenarios.
   * </p>
   *
   * @param clientCert as in {@link #isCertificateKnown(Certificate)}.
   * @return A list of HEI
   *         <a href='https://github.com/erasmus-without-paper/ewp-specs-api-registry#schac-ids'>
   *         SCHAC ID</a>s. May be empty.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException} for more information.
   */
  Collection<String> getHeisCoveredByCertificate(Certificate clientCert)
      throws UnacceptableStalenessException;

  /**
   * Check if a given client certificate is present in the Registry's catalogue.
   *
   * <p>
   * You can use this method when you are developing an EWP API endpoint, and you want to make sure
   * that it will be accessible only to the requesters within the EWP Network.
   * </p>
   *
   * @param clientCert a <b>valid</b> certificate (if it's not valid, you'll get RuntimeExceptions).
   *        Most often, this will be the certificate which the requester have used in his HTTPS
   *        request).
   * @return <b>true</b> if this certificate belongs to someone from the EWP Network.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException}.
   *
   * @see #getHeisCoveredByCertificate(Certificate)
   * @see #isHeiCoveredByCertificate(String, Certificate)
   */
  boolean isCertificateKnown(Certificate clientCert) throws UnacceptableStalenessException;


  /**
   * Check if a given HEI is covered by a given client certificate.
   *
   * @param heiId
   *        <a href='https://github.com/erasmus-without-paper/ewp-specs-api-registry#schac-ids'>
   *        SCHAC ID</a> of the HEI. If you do not know the HEI's SCHAC ID, you may attempt to find
   *        it with the help of {@link #findHeiId(String, String)} method.
   * @param clientCert as in {@link #isCertificateKnown(Certificate)}.
   * @return <b>true</b> if the certificate is known, and HEI is covered by it. <b>False</b>
   *         otherwise.
   * @throws UnacceptableStalenessException if the catalogue copy is "too old". See
   *         {@link UnacceptableStalenessException} for more information.
   */
  boolean isHeiCoveredByCertificate(String heiId, Certificate clientCert)
      throws UnacceptableStalenessException;

  /**
   * Force the client to refresh its internal copy of the EWP catalogue.
   *
   * <p>
   * This method is synchronous - it will block until the catalogue is fully refreshed.
   * </p>
   *
   * <p>
   * Note, that many {@link RegistryClient} implementations (such as {@link ClientImpl}, if called
   * with proper {@link ClientImplOptions}) will be able to refresh their copy of the catalogue
   * automatically (without the need of you calling this method).
   * </p>
   *
   * @throws RefreshFailureException if the Registry Service cannot be contacted.
   */
  void refresh() throws RefreshFailureException;
}
