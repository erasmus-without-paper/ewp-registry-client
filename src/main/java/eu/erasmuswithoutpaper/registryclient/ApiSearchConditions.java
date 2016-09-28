package eu.erasmuswithoutpaper.registryclient;

/**
 * A set of conditions to test against when using
 * {@link RegistryClient#findApi(ApiSearchConditions)}.
 *
 * <p>
 * It describes a set of conditions to test against API entries found in the Registry's catalogue
 * catalogue response (in its <code>&lt;apis-implemented&gt;</code> section). Usually you will use
 * this class in conjunction with {@link RegistryClient#findApi(ApiSearchConditions)} and
 * {@link RegistryClient#findApis(ApiSearchConditions)} methods.
 * </p>
 *
 * @since 1.0.0
 */
public class ApiSearchConditions {

  private String namespaceUri;
  private String localName;
  private String minVersion;
  private String heiId;

  /**
   * Create an empty set of conditions. (Use <code>set*</code> methods to add your conditions.)
   */
  public ApiSearchConditions() {}

  /**
   * Get the SCHAC ID of the HEI which needs to be covered by the API, as it has been previously
   * provided via {@link #setRequiredHei(String)}.
   *
   * @return Either String, or <b>null</b> if no such requirement has been set.
   */
  public String getRequiredHei() {
    return this.heiId;
  }

  /**
   * Get the required API element local name, as it has been previously provided via
   * {@link #setApiClassRequired(String, String)}.
   *
   * @return Either String, or <b>null</b> if no such requirement has been set.
   */
  public String getRequiredLocalName() {
    return this.localName;
  }

  /**
   * Get the minimum required API version, as it has been previously provided via
   * {@link #setMinVersionRequired(String)}.
   *
   * @return Either String, or <b>null</b> if no such requirement has been set.
   */
  public String getRequiredMinVersion() {
    return this.minVersion;
  }

  /**
   * Get the required API element namespace URI, as it has been previously provided via
   * {@link #setApiClassRequired(String, String)}.
   *
   * @return Either String, or <b>null</b> if no such requirement has been set.
   */
  public String getRequiredNamespaceUri() {
    return this.namespaceUri;
  }

  /**
   * Require the API to be of a given class.
   *
   * @param namespaceUri Required namespaceURI of the API entry element, or <b>null</b> if no
   *        requirements should be set.
   * @param localName Required localName of the API entry element, or <b>null</b> if no requirements
   *        should be set.
   * @return This object.
   */
  public ApiSearchConditions setApiClassRequired(String namespaceUri, String localName) {
    this.namespaceUri = namespaceUri;
    this.localName = localName;
    return this;
  }

  /**
   * Require the API to be of a given class, and to have a <code>version</code> attribute greater or
   * equal to the provided one.
   *
   * <p>
   * This method is a shorthand which allows you to call
   * {@link #setApiClassRequired(String, String)} and {@link #setMinVersionRequired(String)} both in
   * one call.
   * </p>
   *
   * @param namespaceUri Required namespaceURI of the API entry element, or <b>null</b> if no
   *        requirements should be set.
   * @param localName Required localName of the API entry element, or <b>null</b> if no requirements
   *        should be set.
   * @param minVersionRequired as described in {@link #setMinVersionRequired(String)}.
   * @return This object.
   * @since 1.2.0
   */
  public ApiSearchConditions setApiClassRequired(String namespaceUri, String localName,
      String minVersionRequired) {
    this.setApiClassRequired(namespaceUri, localName);
    this.setMinVersionRequired(minVersionRequired);
    return this;
  }

  /**
   * Require the API entry to have a <code>version</code> attribute greater or equal to the provided
   * one.
   *
   * <p>
   * Both versions (the one in the matched DOM attribute, and the one provided in this method's
   * parameter) MUST be in the <code>X.Y.Z</code> format, where X, Y and Z are non-negative
   * integers. If you believe that the API entry you are looking for does not follow this versioning
   * schema, then you SHOULD NOT use this method, and verify <code>version</code> attribute
   * yourself.
   * </p>
   *
   * <p>
   * Note, that the major version of most EWP APIs is also included in the API's namespace URI. So,
   * for example, if you're upgrading from <code>"3.5.1"</code> to <code>"4.0.0"</code>, then you
   * will often also be required to change the namespace URI provided in
   * {@link #setApiClassRequired(String, String)}.
   * </p>
   *
   * @param minVersionRequired A minimum required version string, e.g. <code>"1.3.0"</code>, or
   *        <b>null</b> if no requirements should be set.
   * @return This object.
   */
  public ApiSearchConditions setMinVersionRequired(String minVersionRequired) {
    this.minVersion = minVersionRequired;
    return this;
  }

  /**
   * Require the API entry to cover a particular HEI.
   *
   * @param heiId SCHAC ID of the HEI, or <b>null</b> if no requirements should be set.
   * @return This object.
   */
  public ApiSearchConditions setRequiredHei(String heiId) {
    this.heiId = heiId;
    return this;
  }
}
