package eu.erasmuswithoutpaper.registryclient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
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

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import eu.erasmuswithoutpaper.registryclient.CatalogueFetcher.Http200RegistryResponse;
import eu.erasmuswithoutpaper.registryclient.RegistryClient.RegistryClientException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Thread-safe (and mostly immutable) internal representation of the catalogue document.
 */
@SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
class CatalogueDocument {

  @SuppressWarnings({ "serial" })
  static class CatalogueParserException extends RegistryClientException {
    CatalogueParserException(String message) {
      super(message);
    }

    CatalogueParserException(String message, Exception cause) {
      super(message, cause);
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
    this.heiIdMaps = new HashMap<>();
    this.apiIndex = new HashMap<>();
    this.heiEntries = new HashMap<>();

    // Create indexes.

    try {
      for (Element certElem : Utils.asElementList((NodeList) xpath.evaluate(
          "r:host/r:client-credentials-in-use/r:certificate", root, XPathConstants.NODESET))) {
        String fingerprint = certElem.getAttribute("sha-256");
        Set<String> coveredHeis;
        if (this.certHeis.containsKey(fingerprint)) {
          coveredHeis = this.certHeis.get(fingerprint);
        } else {
          coveredHeis = new HashSet<String>();
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
          coveredHeis = new HashSet<String>();
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
      }

    } catch (XPathExpressionException e) {
      throw new RuntimeException(e);
    }
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
      if (!heis.contains(conds.getRequiredHei())) {
        return false;
      }
    }
    return true;
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
          results.add(clone);
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
}
