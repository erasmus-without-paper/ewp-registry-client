package eu.erasmuswithoutpaper.registryclient;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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

  /**
   * Get a new, safely configured instance of {@link DocumentBuilder}.
   *
   * @return a {@link DocumentBuilder} instance.
   */
  public static DocumentBuilder newSecureDocumentBuilder() {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setIgnoringComments(true);

      /*
       * XXE prevention. See here:
       * https://www.owasp.org/index.php/XML_External_Entity_(XXE)_Prevention_Cheat_Sheet#Java
       */
      String feature = null;
      feature = "http://apache.org/xml/features/disallow-doctype-decl";
      dbf.setFeature(feature, true);
      feature = "http://xml.org/sax/features/external-general-entities";
      dbf.setFeature(feature, false);
      feature = "http://xml.org/sax/features/external-parameter-entities";
      dbf.setFeature(feature, false);
      feature = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
      dbf.setFeature(feature, false);
      dbf.setXIncludeAware(false);
      dbf.setExpandEntityReferences(false);

      return dbf.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
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
}
