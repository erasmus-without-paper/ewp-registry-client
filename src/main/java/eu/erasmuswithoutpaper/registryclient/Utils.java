package eu.erasmuswithoutpaper.registryclient;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;

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
   * Transform a {@link NodeList} into a list of {@link Element}s.
   *
   * @param list a {@link NodeList}. It MUST contain {@link Element}s only.
   * @return a list of {@link Element}s.
   */
  @SuppressWarnings("unchecked")
  static List<? extends Element> asElementList(NodeList list) {
    return (List<? extends Element>) (list.getLength() == 0 ? Collections.<Element>emptyList()
        : new NodeListWrapper(list));
  }
}
