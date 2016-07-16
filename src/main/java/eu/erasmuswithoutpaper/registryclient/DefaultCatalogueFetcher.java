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
 * Default implementation of the {@link CatalogueFetcher}.
 *
 * <p>
 * This implementation will be used by the {@link ClientImpl} unless a custom implementation will be
 * set via {@link ClientImplOptions#setCatalogueFetcher(CatalogueFetcher)} method.
 * </p>
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

  @Override
  public RegistryResponse fetchCatalogue(String previousETag) throws IOException {
    logger.debug("Opening HTTPS connection to the Registry API");
    URL url = new URL("https://registry.erasmuswithoutpaper.eu/catalogue-v1.xml");
    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setAllowUserInteraction(false);
    conn.setRequestProperty("If-None-Match", previousETag);
    conn.connect();

    int status = conn.getResponseCode();
    logger.debug("Registry API responded with HTTP " + status);

    /* Adjust the value of "Expires" for the difference in server and client times. */

    long clientTimeNow = System.currentTimeMillis();
    long serverTimeNow = conn.getHeaderFieldDate("Date", clientTimeNow);
    long difference = serverTimeNow - clientTimeNow;
    if (Math.abs(difference) > 60000) {
      logger.debug("Difference in server-client time is " + difference + "ms");
    }
    long serverTimeExpires = conn.getHeaderFieldDate("Expires", clientTimeNow + 300000);
    Date expires = new Date(clientTimeNow + (serverTimeExpires - serverTimeNow));
    logger.debug("Effective expiry time: " + expires);

    switch (status) {
      case 200:
        String newETag = conn.getHeaderField("ETag");
        byte[] content = readEntireStream(conn.getInputStream());
        logger.debug("Read " + content.length + " bytes with ETag " + newETag);
        return new Http200RegistryResponse(content, newETag, expires);
      case 304:
        return new Http304RegistryResponse(expires);
      default:
        throw new IOException("Unexpected Registry API response status: " + status);
    }
  }
}
