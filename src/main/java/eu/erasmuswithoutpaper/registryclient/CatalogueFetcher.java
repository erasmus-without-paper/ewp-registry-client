package eu.erasmuswithoutpaper.registryclient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.Date;

/**
 * Classes implementing this interface are able to provide a raw Registry API catalogue response.
 *
 * @see DefaultCatalogueFetcher
 * @see ClientImplOptions#setCatalogueFetcher(CatalogueFetcher)
 */
public interface CatalogueFetcher {

  /**
   * Represents a HTTP 200 response received from the Registry API.
   */
  public static class Http200RegistryResponse extends RegistryResponse {

    /**
     * Thrown by {@link Http200RegistryResponse#deserialize(byte[])} when the raw data could not be
     * deserialized into a valid {@link Http200RegistryResponse} object.
     */
    @SuppressWarnings("serial")
    static class CouldNotDeserialize extends Exception {
    }

    /**
     * Deserialize an object from a raw byte array. (Used for persistent caching of the catalogue
     * response.)
     */
    static Http200RegistryResponse deserialize(byte[] raw) throws CouldNotDeserialize {
      try {
        try {
          ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(raw));
          int version = in.readInt();
          if (version != 1) {
            throw new CouldNotDeserialize();
          }
          Date expires = (Date) in.readObject();
          byte[] content = (byte[]) in.readObject();
          String etag = (String) in.readObject();
          return new Http200RegistryResponse(content, etag, expires);
        } catch (StreamCorruptedException | ClassNotFoundException e) {
          throw new CouldNotDeserialize();
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private final byte[] content;
    private final String etag;

    /**
     * @param content The response body (raw XML data).
     * @param etag The value of the HTTP ETag header received with the response, or <b>null</b> if
     *        no ETag was present.
     * @param expires The value of the HTTP Expires header received with the response, or
     *        <b>null</b> if no Expires header was present. Implementations are allowed to "deduce"
     *        a proper Expires value from other HTTP headers (i.e. Cache-Control header).
     */
    public Http200RegistryResponse(byte[] content, String etag, Date expires) {
      super(expires);
      this.content = content;
      this.etag = etag;
    }

    byte[] getContent() {
      return content;
    }

    String getETag() {
      return etag;
    }

    /**
     * Serialize this object. (Used for persistent caching of the catalogue response.)
     */
    byte[] serialize() {
      try {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(data);
        out.writeInt(1); // version
        out.writeObject(this.getExpires());
        out.writeObject(this.getContent());
        out.writeObject(this.getETag());
        return data.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Represents a HTTP 304 response received from the Registry API.
   */
  public static class Http304RegistryResponse extends RegistryResponse {

    /**
     * @param expires value of the HTTP Expires header received with the response.
     */
    protected Http304RegistryResponse(Date expires) {
      super(expires);
    }
  }

  /**
   * A common base for both {@link Http200RegistryResponse} and {@link Http304RegistryResponse}
   * responses.
   */
  public abstract static class RegistryResponse {

    private final Date expires;

    /**
     * @param expires The value of the HTTP Expires header received with the response.
     */
    RegistryResponse(Date expires) {
      this.expires = expires;
    }

    /**
     * @return as described in {@link #RegistryResponse(Date)}.
     */
    Date getExpires() {
      return new Date(this.expires.getTime());
    }
  }

  /**
   * Fetch the catalogue from the Registry Service, or confirm that it didn't change.
   *
   * <p>
   * Implementations are strongly advised to include the attached ETag value in their
   * <code>If-None-Match</code> HTTP header when making the request to the Registry API.
   * {@link ClientImpl} is interested in just two types of Registry API responses:
   * </p>
   *
   * <ul>
   * <li>If HTTP 200 is received, then this method must return an {@link Http200RegistryResponse}
   * object.</li>
   * <li>If HTTP 304 is received, then this method must return an {@link Http304RegistryResponse}
   * object.</li>
   * <li>If any other type of response is received from the Registry API, or the Registry API cannot
   * be contacted, then this method must throw an {@link IOException}.</li>
   * </ul>
   *
   * @param etag String or <b>null</b>. If <b>not null</b>, then it should contain the ETag value to
   *        be used in the <code>If-None-Match</code> request header (the ETag representing the
   *        version of the catalogue which we currently possess). If <b>null</b>, then this method
   *        will not use the <code>If-None-Match</code> header (and will therefore be expected to
   *        return an {@link Http200RegistryResponse}).
   * @return Either {@link Http200RegistryResponse} or {@link Http304RegistryResponse} object.
   * @throws IOException if Registry API could not be contacted, or it has responded with a HTTP
   *         status different than 200 and 304.
   */
  RegistryResponse fetchCatalogue(String etag) throws IOException;
}
