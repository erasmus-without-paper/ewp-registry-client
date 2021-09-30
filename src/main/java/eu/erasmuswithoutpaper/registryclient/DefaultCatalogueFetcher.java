package eu.erasmuswithoutpaper.registryclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link CatalogueFetcher} which retrieves its <code>&lt;catalogue&gt;</code> response directly
 * from the <a href='https://registry.erasmuswithoutpaper.eu/'>Registry Service</a>.
 *
 * <p>
 * This default implementation will be used by the {@link ClientImpl} unless a custom implementation
 * will be set via {@link ClientImplOptions#setCatalogueFetcher(CatalogueFetcher)} method.
 * </p>
 *
 * @since 1.0.0
 */
public class DefaultCatalogueFetcher implements CatalogueFetcher {

  private static final Logger logger = LoggerFactory.getLogger(DefaultCatalogueFetcher.class);

  private static byte[] readEntireStream(InputStream is) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int nread;
    byte[] data = new byte[16384];
    while ((nread = is.read(data, 0, data.length)) != -1) {
      buffer.write(data, 0, nread);
    }
    buffer.flush();
    return buffer.toByteArray();
  }

  private final String registryDomain;

  /**
   * Initialize with the default (official) Registry Service (
   * <code>registry.erasmuswithoutpaper.eu</code>).
   */
  public DefaultCatalogueFetcher() {
    this.registryDomain = "registry.erasmuswithoutpaper.eu";
  }

  /**
   * Allows you to use an alternate installation of the Registry Service.
   *
   * <p>
   * In particular, during the development you might want to use
   * <code>dev-registry.erasmuswithoutpaper.eu</code>.
   * </p>
   *
   * @param customRegistryDomain domain name at which an alternate Registry Service installation has
   *        been set up.
   * @since 1.1.0
   */
  public DefaultCatalogueFetcher(String customRegistryDomain) {
    this.registryDomain = customRegistryDomain;
  }

  @Override
  public RegistryResponse fetchCatalogue(String previousETag) throws IOException {
    URL url = new URL("https://" + this.registryDomain + "/catalogue-v1.xml");

    logger.debug("Opening HTTPS connection to {}", url);
    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setAllowUserInteraction(false);
    conn.setRequestProperty("If-None-Match", previousETag);
    conn.setConnectTimeout(10 * 1000); // 10 sec, establish a connection
    conn.setReadTimeout(60 * 1000); // 60 sec, read whole
    conn.connect();

    int status = conn.getResponseCode();
    logger.debug("Registry API responded with HTTP {}", status);
    /* Adjust the value of "Expires" for the difference in server and client times. */

    long clientTimeNow = System.currentTimeMillis();
    long serverTimeNow = conn.getHeaderFieldDate("Date", clientTimeNow);
    long difference = serverTimeNow - clientTimeNow;
    if (Math.abs(difference) > 60000) {
      logger.debug("Difference in server-client time is {} ms", difference);
    }
    long serverTimeExpires = conn.getHeaderFieldDate("Expires", clientTimeNow + 300000);
    Date expires = new Date(clientTimeNow + (serverTimeExpires - serverTimeNow));
    logger.debug("Effective expiry time: {}", expires);

    switch (status) {
      case 200:
        String newETag = conn.getHeaderField("ETag");
        byte[] content = readEntireStream(conn.getInputStream());
        logger.debug("Read {} bytes with ETag {}", content.length, newETag);
        return new Http200RegistryResponse(content, newETag, expires);
      case 304:
        return new Http304RegistryResponse(expires);
      default:
        throw new IOException("Unexpected Registry API response status: " + status);
    }
  }
}
