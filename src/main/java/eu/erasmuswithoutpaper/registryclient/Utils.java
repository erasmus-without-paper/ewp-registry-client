package eu.erasmuswithoutpaper.registryclient;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.interfaces.RSAPublicKey;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.RandomAccess;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class Utils {

  /**
   * Helper class for {@link Utils#asElementList(NodeList)}.
   */
  static final class NodeListWrapper extends AbstractList<Node> implements RandomAccess {
    private final NodeList list;

    /**
     * @param list a {@link NodeList} to be wrapped.
     */
    NodeListWrapper(NodeList list) {
      this.list = list;
    }

    @Override
    public Node get(int index) {
      return this.list.item(index);
    }

    @Override
    public int size() {
      return this.list.getLength();
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(Utils.class);

  private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
    try {
      factory.setFeature(feature, value);
    } catch (ParserConfigurationException e) {
      logger.warn("Your system's default DocumentBuilderFactory doesn't support the \"" + feature
          + "\" feature. See https://github.com/erasmus-without-paper/ewp-registry-client/issues/2");
    }
  }

  /**
   * Transform a {@link NodeList} into a {@link List} of {@link Element}s.
   *
   * @param list a {@link NodeList}. It MUST contain {@link Element}s only.
   * @return a list of {@link Element}s.
   */
  @SuppressWarnings("unchecked")
  static List<? extends Element> asElementList(NodeList list) {
    return (List<? extends Element>) (list.getLength() == 0 ? Collections.<Element>emptyList()
        : new NodeListWrapper(list));
  }

  /**
   * Transform a {@link NodeList} into a {@link List} of {@link Node}s.
   *
   * @param list a {@link NodeList}.
   * @return a list of {@link Node}s.
   */
  static List<? extends Node> asNodeList(NodeList list) {
    return list.getLength() == 0 ? Collections.<Node>emptyList() : new NodeListWrapper(list);
  }

  static String extractFingerprint(Certificate cert) {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    try {
      md.update(cert.getEncoded());
    } catch (CertificateEncodingException e) {
      throw new RuntimeException(e);
    }
    byte[] binDigest = md.digest();
    return DatatypeConverter.printHexBinary(binDigest).toLowerCase(Locale.ENGLISH);
  }

  static String extractFingerprint(RSAPublicKey publicKey) {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    md.update(publicKey.getEncoded());
    byte[] binDigest = md.digest();
    return DatatypeConverter.printHexBinary(binDigest).toLowerCase(Locale.ENGLISH);
  }

  /**
   * Get a new, safely configured instance of {@link DocumentBuilder}.
   *
   * @return a {@link DocumentBuilder} instance.
   */
  static DocumentBuilder newSecureDocumentBuilder() {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setIgnoringComments(true);

      /*
       * XXE prevention. See here:
       * https://www.owasp.org/index.php/XML_External_Entity_(XXE)_Prevention_Cheat_Sheet#Java
       */
      trySetFeature(dbf, "http://apache.org/xml/features/disallow-doctype-decl", true);
      trySetFeature(dbf, "http://xml.org/sax/features/external-general-entities", false);
      trySetFeature(dbf, "http://xml.org/sax/features/external-parameter-entities", false);
      trySetFeature(dbf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      dbf.setXIncludeAware(false);
      dbf.setExpandEntityReferences(false);

      return dbf.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
  }
}
