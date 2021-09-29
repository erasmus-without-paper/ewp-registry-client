package eu.erasmuswithoutpaper.registryclient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import eu.erasmuswithoutpaper.registryclient.CatalogueFetcher.Http200RegistryResponse;
import eu.erasmuswithoutpaper.registryclient.RegistryClient.InvalidApiEntryElement;
import eu.erasmuswithoutpaper.registryclient.RegistryClient.RegistryClientException;
import eu.erasmuswithoutpaper.registryclient.RegistryClient.StaleApiEntryElement;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;
import org.w3c.dom.UserDataHandler;
import org.xml.sax.SAXException;

/**
 * Thread-safe (and mostly immutable) internal representation of the catalogue document.
 */
@SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
class CatalogueDocument {

  private static final Logger logger = LoggerFactory.getLogger(CatalogueDocument.class);

  /**
   * The underlying catalogue document.
   *
   * <p>
   * This field is final, but its data is still mutable (and thus, not thread-safe). Thus, clones
   * need to be created whenever the elements of this document are exposed outside.
   * </p>
   */
  private final Document doc;

  /**
   * This is the ETag we got along the retrieved catalogue document.
   */
  private final String etag;

  /**
   * "SHA-256 -> heiIds" index for {@link #doc}.
   *
   * <p>
   * Client certificate's SHA-256 hex fingerprint is mapped to the set of all HEI IDs covered by
   * this certificate (can be empty).
   * </p>
   *
   * <p>
   * This field is final, but its data is still mutable (and thus, not thread-safe). Unmodifiable
   * views need to be used before its values are exposed outside.
   * </p>
   */
  private final Map<String, Set<String>> certHeis;

  private final Map<String, Set<String>> cliKeyHeis;

  /**
   * "Host Element -> heiIds" index for {@link #doc}.
   *
   * <p>
   * For each <code>&lt;host&gt;</code> element in the catalogue, a set of HEI IDs covered by this
   * host (can be empty).
   * </p>
   *
   * <p>
   * This field is final, but its data is still mutable (and thus, not thread-safe). Unmodifiable
   * views need to be used before its values are exposed outside.
   * </p>
   */
  private final Map<Element, Set<String>> hostHeis;

  /**
   * "Host Element -> rsa-server-key fingerprints" index for {@link #doc}.
   *
   * <p>
   * For each <code>&lt;host&gt;</code> element in the catalogue, a set of rsa-server-key
   * fingerprints covering this host (can be empty).
   * </p>
   *
   * <p>
   * This field is final, but its data is still mutable (and thus, not thread-safe). Unmodifiable
   * views need to be used before its values are exposed outside.
   * </p>
   */
  private final Map<Element, Set<String>> hostServerKeys;

  /**
   * "HEI other-id type -> other-id value -> heiId" index for {@link #doc}.
   *
   * <p>
   * We keep a separate map for each <code>type</code> attribute of <code>&lt;other-id&gt;</code>
   * elements present in the {@link #doc}. The keys of each such map contain all values present for
   * the type, and the value contains a single HEI ID mapped for this value (if there are many HEI
   * IDs mapped for this value (which should not happen in general) then a random one is stored
   * here).
   * </p>
   *
   * <p>
   * This field is final, but its data is still mutable (and thus, not thread-safe). Unmodifiable
   * views need to be used before its values are exposed outside.
   * </p>
   */
  private final Map<String, Map<String, String>> heiIdMaps;

  /**
   * "heiId -> HeiEntry" index for {@link #doc}.
   *
   * <p>
   * This field is final, but its data is still mutable (and thus, not thread-safe). Unmodifiable
   * views need to be used before its values are exposed outside.
   * </p>
   */
  private final Map<String, HeiEntry> heiEntries;

  /**
   * "Unique API ID -> API entry elements" index for {@link #doc}.
   *
   * <p>
   * Unique API ID is constructed from both namespaceUri and localName of the API entry element (see
   * {@link #getApiIndexKey(String, String)}). Each such ID is mapped to a list of all DOM
   * {@link Element}s found under <code>&lt;apis-implemented&gt;</code> element in the {@link #doc}.
   * </p>
   *
   * <p>
   * This field is final, but its data is still mutable (and thus, not thread-safe). Unmodifiable
   * views need to be used before its values are exposed outside.
   * </p>
   */
  private final Map<String, List<Element>> apiIndex;

  /**
   * "SHA-256 -> RSA public key" index for {@link #doc}.
   *
   * <p>
   * This map holds RSA public keys parsed from catalogue's binaries.
   * </p>
   */
  private final HashMap<String, RSAPublicKey> keyBodies;

  /**
   * Indicates the time after which this copy of the catalogue should be considered stale. It is
   * okay to serve stale copies for a while, but the client should schedule an "is it still
   * up-to-date?" check.
   *
   * <p>
   * <b>This field is mutable.</b> It can be modified via {@link #extendExpiryDate(Date)}.
   * </p>
   */
  private volatile Date expires;

  /**
   * Parse the response received from the Registry Service and create a new
   * {@link CatalogueDocument} based on it.
   *
   * @param registryResponse The {@link Http200RegistryResponse} response received from the Registry
   *        Service.
   * @throws CatalogueParserException if registryResponse did not contain a valid catalogue.
   */
  CatalogueDocument(Http200RegistryResponse registryResponse) throws CatalogueParserException {

    DocumentBuilder docBuilder = Utils.newSecureDocumentBuilder();

    this.expires = registryResponse.getExpires();
    if (this.expires == null) {
      // It seems that the Registry didn't supply the "Expires" header.
      // (In general, this shouldn't happen.)
      logger.warn("Missing 'Expires' header in catalogue response. Will assume 5 minutes.");
      this.expires = new Date((new Date().getTime()) + 1000 * 60 * 5);
    }
    this.etag = registryResponse.getETag();

    // Parse it.

    try {
      this.doc = docBuilder.parse(new ByteArrayInputStream(registryResponse.getContent()));
    } catch (SAXException e) {
      throw new CatalogueParserException("Problem parsing the catalogue response", e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // Run a basic validation. (Just a sanity check. No detailed validation is necessary.)

    Element root = this.doc.getDocumentElement();
    if (root.getNamespaceURI() == null
        || (!root.getNamespaceURI().equals(RegistryClient.REGISTRY_CATALOGUE_V1_NAMESPACE_URI))) {
      throw new CatalogueParserException("Catalogue namespace URI mismatch.");
    }
    if (!root.getLocalName().equals("catalogue")) {
      throw new CatalogueParserException("Catalogue localName mismatch.");
    }

    // Prepare dependencies for traversal.

    XPathFactory xpathfactory = XPathFactory.newInstance();
    XPath xpath = xpathfactory.newXPath();
    xpath.setNamespaceContext(new NamespaceContext() {

      @Override
      public String getNamespaceURI(String prefix) {
        if ("r".equals(prefix)) {
          return RegistryClient.REGISTRY_CATALOGUE_V1_NAMESPACE_URI;
        }
        throw new IllegalArgumentException(prefix);
      }

      @Override
      public String getPrefix(String namespaceUri) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Iterator<String> getPrefixes(String namespaceUri) {
        throw new UnsupportedOperationException();
      }
    });

    this.certHeis = new HashMap<>();
    this.cliKeyHeis = new HashMap<>();
    this.hostHeis = new HashMap<>();
    this.hostServerKeys = new HashMap<>();
    this.heiIdMaps = new HashMap<>();
    this.apiIndex = new HashMap<>();
    this.heiEntries = new HashMap<>();
    this.keyBodies = new HashMap<>();

    // Create indexes.

    try {
      for (Element certElem : Utils.asElementList((NodeList) xpath.evaluate(
          "r:host/r:client-credentials-in-use/r:certificate", root, XPathConstants.NODESET))) {
        String fingerprint = certElem.getAttribute("sha-256");
        Set<String> coveredHeis;
        if (this.certHeis.containsKey(fingerprint)) {
          coveredHeis = this.certHeis.get(fingerprint);
        } else {
          coveredHeis = new HashSet<>();
          this.certHeis.put(fingerprint, coveredHeis);
        }
        for (Element heiIdElem : Utils.asElementList((NodeList) xpath
            .evaluate("../../r:institutions-covered/r:hei-id", certElem, XPathConstants.NODESET))) {
          coveredHeis.add(heiIdElem.getTextContent());
        }
      }
      for (Element cliKeyElem : Utils.asElementList((NodeList) xpath.evaluate(
          "r:host/r:client-credentials-in-use/r:rsa-public-key", root, XPathConstants.NODESET))) {
        String fingerprint = cliKeyElem.getAttribute("sha-256");
        Set<String> coveredHeis;
        if (this.cliKeyHeis.containsKey(fingerprint)) {
          coveredHeis = this.cliKeyHeis.get(fingerprint);
        } else {
          coveredHeis = new HashSet<>();
          this.cliKeyHeis.put(fingerprint, coveredHeis);
        }
        for (Element heiIdElem : Utils
            .asElementList((NodeList) xpath.evaluate("../../r:institutions-covered/r:hei-id",
                cliKeyElem, XPathConstants.NODESET))) {
          coveredHeis.add(heiIdElem.getTextContent());
        }
      }
      for (Element otherIdElem : Utils.asElementList((NodeList) xpath
          .evaluate("r:institutions/r:hei/r:other-id", root, XPathConstants.NODESET))) {
        String type = otherIdElem.getAttribute("type");
        String value = otherIdElem.getTextContent();
        String heiId = ((Element) otherIdElem.getParentNode()).getAttribute("id");

        Map<String, String> mapForType = this.heiIdMaps.get(type);
        if (mapForType == null) {
          mapForType = new HashMap<>();
          this.heiIdMaps.put(type, mapForType);
        }

        mapForType.put(getCanonicalId(value), heiId);
      }
      for (Element heiElem : Utils.asElementList(
          (NodeList) xpath.evaluate("r:institutions/r:hei", root, XPathConstants.NODESET))) {
        String id = heiElem.getAttribute("id");
        HeiEntry hei = new HeiEntryImpl(id, heiElem);
        this.heiEntries.put(id, hei);
      }
      for (Element apiElem : Utils.asElementList(
          (NodeList) xpath.evaluate("r:host/r:apis-implemented/*", root, XPathConstants.NODESET))) {

        // newApiIndex's keys uniquely identify API's namespaceURI and localName.

        String key = getApiIndexKey(apiElem.getNamespaceURI(), apiElem.getLocalName());
        List<Element> entries = this.apiIndex.get(key);
        if (entries == null) {
          entries = new ArrayList<>();
          this.apiIndex.put(key, entries);
        }

        // entries - the list of all API entry elements for this key.

        entries.add(apiElem);
      }
      for (Element hostElem : Utils
          .asElementList((NodeList) xpath.evaluate("r:host", root, XPathConstants.NODESET))) {
        Set<String> heis = new HashSet<>();
        this.hostHeis.put(hostElem, heis);
        for (Element heiIdElem : Utils.asElementList((NodeList) xpath
            .evaluate("r:institutions-covered/r:hei-id", hostElem, XPathConstants.NODESET))) {
          String heiId = heiIdElem.getTextContent();
          heis.add(heiId);
        }
        Set<String> keys = new HashSet<>();
        this.hostServerKeys.put(hostElem, keys);
        for (Element keyElem : Utils
            .asElementList((NodeList) xpath.evaluate("r:server-credentials-in-use/r:rsa-public-key",
                hostElem, XPathConstants.NODESET))) {
          String fingerprint = keyElem.getAttribute("sha-256");
          keys.add(fingerprint);
        }
      }

      KeyFactory rsaKeyFactory;
      try {
        rsaKeyFactory = KeyFactory.getInstance("RSA");
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }
      for (Element keyElem : Utils.asElementList(
          (NodeList) xpath.evaluate("r:binaries/r:rsa-public-key", root, XPathConstants.NODESET))) {
        String fingerprint = keyElem.getAttribute("sha-256");
        byte[] data = DatatypeConverter.parseBase64Binary(keyElem.getTextContent());
        X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
        RSAPublicKey value;
        try {
          value = (RSAPublicKey) rsaKeyFactory.generatePublic(spec);
          this.keyBodies.put(fingerprint, value);
        } catch (InvalidKeySpecException | ClassCastException e) {
          if (logger.isWarnEnabled()) {
            logger.warn("Could not load object " + fingerprint + " as RSAPublicKey: " + e);
          }
        }
      }

    } catch (XPathExpressionException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getApiIndexKey(String namespaceUri, String localName) {
    return "{" + namespaceUri + "}" + localName;
  }

  /**
   * Convert <code>&lt;other-id&gt;</code> value to its canonical form.
   */
  private static String getCanonicalId(String value) {
    return value.trim().toLowerCase(Locale.ENGLISH);
  }

  /**
   * Check if first version string matches the "minimum required" version string in the second
   * argument.
   *
   * <p>
   * Both strings MUST be in thr "X.Y.Z" format, where X, Y and Z are non-negative integers (a
   * subset of semantic versioning strings). If this requirement is not met, this method will not
   * attempt to compare the strings, and it will simply return <code>false</code>.
   * </p>
   *
   * <ul>
   * <li><code>("1.6.0", "1.10.0") == false</code>,</li>
   * <li><code>("1.10.0", "1.6.0") == true</code>,</li>
   * <li><code>("1.6.0", "1.6.0") == true</code>,</li>
   * <li><code>("1.10", "1.6.0") == false</code> (because the first one is invalid),</li>
   * <li><code>("1.10.0", "1.6.0x") == false</code> (because the second one is invalid).</li>
   * </ul>
   *
   * @param apiVersion string of 3 ordinal numbers separated by dots.
   * @param minRequiredVersion string of 3 ordinal numbers separated by dots.
   * @return <b>true</b> if both arguments are valid version strings, and the first one is equal or
   *         greater than the second one.
   */
  static boolean doesVersionXMatchMinimumRequiredVersionY(String apiVersion,
      String minRequiredVersion) {
    String[] s1 = apiVersion.split("\\.");
    String[] s2 = minRequiredVersion.split("\\.");
    if (s1.length != 3 || s2.length != 3) {
      return false;
    }
    int[] i1 = new int[3];
    int[] i2 = new int[3];
    try {
      for (int i = 0; i < 3; i++) {
        i1[i] = Integer.parseInt(s1[i]);
        i2[i] = Integer.parseInt(s2[i]);
      }
    } catch (NumberFormatException e) {
      return false;
    }
    for (int i = 0; i < 3; i++) {
      if (i1[i] > i2[i]) {
        return true;
      } else if (i1[i] < i2[i]) {
        return false;
      }
    }
    return true;
  }

  public boolean isApiCoveredByServerKey(Element apiElement, RSAPublicKey serverKey)
      throws InvalidApiEntryElement {
    return this.extractFingerprintsForApiElement(apiElement)
        .contains(Utils.extractFingerprint(serverKey));
  }

  @Override
  public String toString() {
    return "CatalogueDocument[ETag=" + this.getETag() + ", Expires=" + this.getExpiryDate() + "]";
  }

  private boolean doesElementMatchConditions(Element elem, ApiSearchConditions conds) {
    if (conds.getRequiredNamespaceUri() != null
        && (!conds.getRequiredNamespaceUri().equals(elem.getNamespaceURI()))) {
      return false;
    }
    if (conds.getRequiredLocalName() != null
        && (!conds.getRequiredLocalName().equals(elem.getLocalName()))) {
      return false;
    }
    if (conds.getRequiredMinVersion() != null) {
      String attrVer = elem.getAttribute("version");
      if (attrVer.isEmpty()) {
        return false;
      }
      if (!doesVersionXMatchMinimumRequiredVersionY(attrVer, conds.getRequiredMinVersion())) {
        return false;
      }
    }
    if (conds.getRequiredHei() != null) {
      Element hostElem = (Element) elem.getParentNode().getParentNode();
      Set<String> heis = this.hostHeis.get(hostElem);
      if (heis == null) {
        return false;
      }
      return heis.contains(conds.getRequiredHei());
    }
    return true;
  }

  private Set<String> extractFingerprintsForApiElement(Element apiElement) {

    // Extract the meta object, which we store in the ApiEntryElement wrapper.

    if (!(apiElement instanceof ApiEntryElement)) {
      throw new InvalidApiEntryElement();
    }
    InternalApiEntryAttachment meta = ((ApiEntryElement) apiElement).internalApiEntryAttachment;

    // Verify if the meta object is not yet stale. We want to force the clients
    // to NOT cache these apiElements.

    if (meta.isStale()) {
      logger.warn("Stale apiElements in use. Possible memory leaks. See: "
          + "https://github.com/erasmus-without-paper/ewp-registry-client/issues/8");
      throw new StaleApiEntryElement();
    }

    // Use the CatalogueDocument from the meta object, instead of "this".
    // This will fix the issue of clients getting wrong results, but may cause
    // more memory leaks, if clients cache apiElements somewhere.

    if (!meta.catalogueDocument.hostServerKeys.containsKey(meta.host)) {
      // The host of this API doesn't have *any* server keys.
      return new HashSet<>();
    }
    return meta.catalogueDocument.hostServerKeys.get(meta.host);
  }

  /**
   * Extend the expiry date of the document.
   *
   * <p>
   * Expiry date can be moved into the future once it is confirmed that this version of the document
   * (as identified by its {@link #getETag()}) is still up-to-date.
   * </p>
   *
   * @param newExpiryDate The new expiry date. It needs to be <b>after</b> the previously used one,
   *        otherwise it won't be changed.
   */
  synchronized void extendExpiryDate(Date newExpiryDate) {
    if (newExpiryDate.after(this.expires)) {
      this.expires = new Date(newExpiryDate.getTime());
    }
  }

  /**
   * This implements {@link RegistryClient#findApi(ApiSearchConditions)}, but only for this
   * particular version of the catalogue document.
   */
  Element findApi(ApiSearchConditions conditions) {
    Element bestChoice = null;
    for (Element entry : this.findApis(conditions)) {
      if (bestChoice == null) {
        bestChoice = entry;
      } else if (bestChoice.getAttribute("version").length() == 0) {
        bestChoice = entry;
      } else {
        String currentBest = bestChoice.getAttribute("version");
        String newCandidate = entry.getAttribute("version");
        if (doesVersionXMatchMinimumRequiredVersionY(newCandidate, currentBest)) {
          bestChoice = entry;
        }
      }
    }
    return bestChoice;
  }

  /**
   * This implements {@link RegistryClient#findApis(ApiSearchConditions)}, but only for this
   * particular version of the catalogue document.
   */
  Collection<Element> findApis(ApiSearchConditions conditions) {
    // First, determine the minimum set of elements we need to look through.

    List<List<Element>> lookupBase = this.getApiLookupBase(conditions);

    // Then, iterate through all the elements and filter the ones that match.

    List<Element> results = new ArrayList<>();
    for (List<Element> lst : lookupBase) {
      for (Element elem : lst) {
        if (this.doesElementMatchConditions(elem, conditions)) {
          // Create a copy of the element, so that it's thread-safe.
          Element clone = (Element) elem.cloneNode(true);
          ApiEntryElement apiEntryElement = new ApiEntryElement(clone,
              new InternalApiEntryAttachment(this, (Element) elem.getParentNode().getParentNode()));
          results.add(apiEntryElement);
        }
      }
    }
    return results;
  }

  /**
   * This implements {@link RegistryClient#findHei(String)}, but only for this particular version of
   * the catalogue document.
   */
  HeiEntry findHei(String id) {
    return this.heiEntries.get(id);
  }

  /**
   * This implements {@link RegistryClient#findHei(String, String)}, but only for this particular
   * version of the catalogue document.
   */
  HeiEntry findHei(String type, String value) {
    String heiId = this.findHeiId(type, value);
    if (heiId == null) {
      return null;
    }
    return this.findHei(heiId);
  }

  /**
   * This implements {@link RegistryClient#findHeiId(String, String)}, but only for this particular
   * version of the catalogue document.
   */
  String findHeiId(String type, String value) {
    value = getCanonicalId(value);
    Map<String, String> mapForType = this.heiIdMaps.get(type);
    if (mapForType == null) {
      return null;
    }
    // It's thread-safe (Strings are immutable).
    return mapForType.get(value);
  }

  /**
   * This implements {@link RegistryClient#findHeis(ApiSearchConditions)}, but only for this
   * particular version of the catalogue document.
   */
  Collection<HeiEntry> findHeis(ApiSearchConditions conditions) {

    // First, determine the minimum set of elements we need to look through.

    List<List<Element>> lookupBase = this.getApiLookupBase(conditions);

    // Then, find all <host> elements which include the matched APIs.

    Set<Element> hostElems = new HashSet<>();
    for (List<Element> lst : lookupBase) {
      for (Element apiElem : lst) {
        if (this.doesElementMatchConditions(apiElem, conditions)) {
          Element hostElem = (Element) apiElem.getParentNode().getParentNode();
          hostElems.add(hostElem);
        }
      }
    }

    // Finally, collect the unique HEI entries covered by these hosts.

    Set<HeiEntry> results = new HashSet<>();
    for (Element hostElem : hostElems) {
      Set<String> heiIds = this.hostHeis.get(hostElem);
      for (String heiId : heiIds) {
        HeiEntry hei = this.heiEntries.get(heiId);
        if (hei == null) {
          // Should not happen, but just in case.
          continue;
        }
        results.add(hei);
      }
    }
    return results;
  }

  RSAPublicKey findRsaPublicKey(String fingerprint) {
    return this.keyBodies.get(fingerprint);
  }

  /**
   * This implements {@link RegistryClient#getAllHeis()}, but only for this particular version of
   * the catalogue document.
   */
  Collection<HeiEntry> getAllHeis() {
    return Collections.unmodifiableCollection(this.heiEntries.values());
  }

  List<List<Element>> getApiLookupBase(ApiSearchConditions conditions) {
    List<List<Element>> lookupBase = new ArrayList<>();
    if (conditions.getRequiredNamespaceUri() != null && conditions.getRequiredLocalName() != null) {

      // We can make use of our namespaceUri+localName index in this case.

      List<Element> match = this.apiIndex.get(
          getApiIndexKey(conditions.getRequiredNamespaceUri(), conditions.getRequiredLocalName()));
      if (match != null) {
        lookupBase.add(match);
      }
    } else {

      // We do not have such an index. We'll need to browse through all entries.

      lookupBase.addAll(this.apiIndex.values());
    }
    return lookupBase;
  }

  /**
   * @return ETag of this document.
   */
  String getETag() {
    return this.etag;
  }

  /**
   * @return expiry date of this document (it can change in time, see
   *         {@link #extendExpiryDate(Date)}).
   */
  Date getExpiryDate() {
    return new Date(this.expires.getTime());
  }

  /**
   * This implements {@link RegistryClient#getHeisCoveredByCertificate(Certificate)}, but only for
   * this particular version of the catalogue document.
   */
  Collection<String> getHeisCoveredByCertificate(Certificate clientCert) {
    String fingerprint = Utils.extractFingerprint(clientCert);
    Set<String> heis = this.certHeis.get(fingerprint);
    if (heis == null) {
      heis = new HashSet<>();
    }
    return Collections.unmodifiableSet(heis);
  }

  Collection<String> getHeisCoveredByClientKey(RSAPublicKey clientKey) {
    String fingerprint = Utils.extractFingerprint(clientKey);
    Set<String> heis = this.cliKeyHeis.get(fingerprint);
    if (heis == null) {
      heis = new HashSet<>();
    }
    return Collections.unmodifiableSet(heis);
  }

  RSAPublicKey getServerKeyCoveringApi(Element apiElement) {
    for (String fingerprint : this.extractFingerprintsForApiElement(apiElement)) {
      RSAPublicKey key = this.findRsaPublicKey(fingerprint);
      if (key != null) {
        return key;
      } else {
        logger.warn("Catalogue contains a reference to a non-existent key {}"
            + ". We will ignore this reference, but this shouldn't happen and should be "
            + "investigated.", fingerprint);
      }
    }
    return null;
  }

  Collection<RSAPublicKey> getServerKeysCoveringApi(Element apiElement) {
    List<RSAPublicKey> result = new ArrayList<>();
    for (String fingerprint : this.extractFingerprintsForApiElement(apiElement)) {
      RSAPublicKey key = this.findRsaPublicKey(fingerprint);
      if (key != null) {
        result.add(key);
      } else {
        logger.warn("Catalogue contains a reference to a non-existent key {}"
            + ". We will ignore this reference, but this shouldn't happen and should be "
            + "investigated.", fingerprint);
      }
    }
    return result;
  }

  /**
   * This implements {@link RegistryClient#isCertificateKnown(Certificate)}, but only for this
   * particular version of the catalogue document.
   */
  boolean isCertificateKnown(Certificate clientCert) {
    String fingerprint = Utils.extractFingerprint(clientCert);
    return this.certHeis.containsKey(fingerprint);
  }

  boolean isClientKeyKnown(RSAPublicKey clientKey) {
    String fingerprint = Utils.extractFingerprint(clientKey);
    return this.cliKeyHeis.containsKey(fingerprint);
  }

  /**
   * Instances of this class get attached to the Elements returned by
   * {@link CatalogueDocument#findApis(ApiSearchConditions)} and
   * {@link CatalogueDocument#findApi(ApiSearchConditions)} methods.
   */
  private static class InternalApiEntryAttachment {

    /**
     * The parent {@link CatalogueDocument}, from which this API entry originated from.
     */
    private final CatalogueDocument catalogueDocument;

    /**
     * The parent <code>&lt;host&gt;</code> element which this API entry has been cloned from.
     */
    private final Element host;

    /**
     * The time when this object was created, in the Date.getTime format (number of milliseconds
     * since January 1, 1970, 00:00:00 GMT).
     */
    private final long created;

    private InternalApiEntryAttachment(CatalogueDocument catalogueDocument, Element host) {
      this.catalogueDocument = catalogueDocument;
      this.host = host;
      this.created = new Date().getTime();
    }

    public boolean isStale() {
      long now = new Date().getTime();
      long diff = now - this.created;
      return diff > 60000; // one minute
    }
  }

  @SuppressWarnings("serial")
  static class CatalogueParserException extends RegistryClientException {
    CatalogueParserException(String message) {
      super(message);
    }

    CatalogueParserException(String message, Exception cause) {
      super(message, cause);
    }
  }

  private static class ApiEntryElement implements Element {

    private final Element element;
    private final InternalApiEntryAttachment internalApiEntryAttachment;

    public ApiEntryElement(Element element, InternalApiEntryAttachment internalApiEntryAttachment) {
      this.element = element;
      this.internalApiEntryAttachment = internalApiEntryAttachment;
    }

    @Override
    public String getTagName() {
      return element.getTagName();
    }

    @Override
    public String getAttribute(String name) {
      return element.getAttribute(name);
    }

    @Override
    public void setAttribute(String name, String value) throws DOMException {
      element.setAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) throws DOMException {
      element.removeAttribute(name);
    }

    @Override
    public Attr getAttributeNode(String name) {
      return element.getAttributeNode(name);
    }

    @Override
    public Attr setAttributeNode(Attr newAttr) throws DOMException {
      return element.setAttributeNode(newAttr);
    }

    @Override
    public Attr removeAttributeNode(Attr oldAttr) throws DOMException {
      return element.removeAttributeNode(oldAttr);
    }

    @Override
    public NodeList getElementsByTagName(String name) {
      return element.getElementsByTagName(name);
    }

    @Override
    public String getAttributeNS(String namespaceUri, String localName) throws DOMException {
      return element.getAttributeNS(namespaceUri, localName);
    }

    @Override
    public void setAttributeNS(String namespaceUri, String qualifiedName, String value)
        throws DOMException {
      element.setAttributeNS(namespaceUri, qualifiedName, value);
    }

    @Override
    public void removeAttributeNS(String namespaceUri, String localName) throws DOMException {
      element.removeAttributeNS(namespaceUri, localName);
    }

    @Override
    public Attr getAttributeNodeNS(String namespaceUri, String localName) throws DOMException {
      return element.getAttributeNodeNS(namespaceUri, localName);
    }

    @Override
    public Attr setAttributeNodeNS(Attr newAttr) throws DOMException {
      return element.setAttributeNodeNS(newAttr);
    }

    @Override
    public NodeList getElementsByTagNameNS(String namespaceUri, String localName)
        throws DOMException {
      return element.getElementsByTagNameNS(namespaceUri, localName);
    }

    @Override
    public boolean hasAttribute(String name) {
      return element.hasAttribute(name);
    }

    @Override
    public boolean hasAttributeNS(String namespaceUri, String localName) throws DOMException {
      return element.hasAttributeNS(namespaceUri, localName);
    }

    @Override
    public TypeInfo getSchemaTypeInfo() {
      return element.getSchemaTypeInfo();
    }

    @Override
    public void setIdAttribute(String name, boolean isId) throws DOMException {
      element.setIdAttribute(name, isId);
    }

    @Override
    public void setIdAttributeNS(String namespaceUri, String localName, boolean isId)
        throws DOMException {
      element.setIdAttributeNS(namespaceUri, localName, isId);
    }

    @Override
    public void setIdAttributeNode(Attr idAttr, boolean isId) throws DOMException {
      element.setIdAttributeNode(idAttr, isId);
    }

    @Override
    public String getNodeName() {
      return element.getNodeName();
    }

    @Override
    public String getNodeValue() throws DOMException {
      return element.getNodeValue();
    }

    @Override
    public void setNodeValue(String nodeValue) throws DOMException {
      element.setNodeValue(nodeValue);
    }

    @Override
    public short getNodeType() {
      return element.getNodeType();
    }

    @Override
    public Node getParentNode() {
      return element.getParentNode();
    }

    @Override
    public NodeList getChildNodes() {
      return element.getChildNodes();
    }

    @Override
    public Node getFirstChild() {
      return element.getFirstChild();
    }

    @Override
    public Node getLastChild() {
      return element.getLastChild();
    }

    @Override
    public Node getPreviousSibling() {
      return element.getPreviousSibling();
    }

    @Override
    public Node getNextSibling() {
      return element.getNextSibling();
    }

    @Override
    public NamedNodeMap getAttributes() {
      return element.getAttributes();
    }

    @Override
    public Document getOwnerDocument() {
      return element.getOwnerDocument();
    }

    @Override
    public Node insertBefore(Node newChild, Node refChild) throws DOMException {
      return element.insertBefore(newChild, refChild);
    }

    @Override
    public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
      return element.replaceChild(newChild, oldChild);
    }

    @Override
    public Node removeChild(Node oldChild) throws DOMException {
      return element.removeChild(oldChild);
    }

    @Override
    public Node appendChild(Node newChild) throws DOMException {
      return element.appendChild(newChild);
    }

    @Override
    public boolean hasChildNodes() {
      return element.hasChildNodes();
    }

    @Override
    public Node cloneNode(boolean deep) {
      return element.cloneNode(deep);
    }

    @Override
    public void normalize() {
      element.normalize();
    }

    @Override
    public boolean isSupported(String feature, String version) {
      return element.isSupported(feature, version);
    }

    @Override
    public String getNamespaceURI() {
      return element.getNamespaceURI();
    }

    @Override
    public String getPrefix() {
      return element.getPrefix();
    }

    @Override
    public void setPrefix(String prefix) throws DOMException {
      element.setPrefix(prefix);
    }

    @Override
    public String getLocalName() {
      return element.getLocalName();
    }

    @Override
    public boolean hasAttributes() {
      return element.hasAttributes();
    }

    @Override
    public String getBaseURI() {
      return element.getBaseURI();
    }

    @Override
    public short compareDocumentPosition(Node other) throws DOMException {
      return element.compareDocumentPosition(other);
    }

    @Override
    public String getTextContent() throws DOMException {
      return element.getTextContent();
    }

    @Override
    public void setTextContent(String textContent) throws DOMException {
      element.setTextContent(textContent);
    }

    @Override
    public boolean isSameNode(Node other) {
      return element.isSameNode(other);
    }

    @Override
    public String lookupPrefix(String namespaceUri) {
      return element.lookupPrefix(namespaceUri);
    }

    @Override
    public boolean isDefaultNamespace(String namespaceUri) {
      return element.isDefaultNamespace(namespaceUri);
    }

    @Override
    public String lookupNamespaceURI(String prefix) {
      return element.lookupNamespaceURI(prefix);
    }

    @Override
    public boolean isEqualNode(Node arg) {
      return element.isEqualNode(arg);
    }

    @Override
    public Object getFeature(String feature, String version) {
      return element.getFeature(feature, version);
    }

    @Override
    public Object setUserData(String key, Object data, UserDataHandler handler) {
      return element.setUserData(key, data, handler);
    }

    @Override
    public Object getUserData(String key) {
      return element.getUserData(key);
    }
  }
}
