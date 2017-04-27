package eu.erasmuswithoutpaper.registryclient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;

import org.w3c.dom.Element;
import org.w3c.dom.Node;


class HeiEntryImpl implements HeiEntry {

  private static class Extras {
    private final Map<String, String> allNames;
    private final Map<String, List<String>> otherIds;

    private Extras(HeiEntryImpl hei) {
      this.allNames = new HashMap<>();
      this.otherIds = new HashMap<>();
      for (Node node : Utils.asNodeList(hei.elem.getChildNodes())) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }
        Element elem = (Element) node;
        String value = elem.getTextContent();
        switch (elem.getTagName()) {
          case "name":
            String lang = elem.getAttributeNS(XMLConstants.XML_NS_URI, "lang");
            if (value.length() > 0) {
              this.allNames.put(lang, value);
            }
            break;

          case "other-id":
            String idType = elem.getAttribute("type");
            List<String> lst = this.otherIds.get(idType);
            if (lst == null) {
              lst = new ArrayList<>();
              this.otherIds.put(idType, lst);
            }
            lst.add(value);
            break;

          default:
            // Ingore.
        }
      }
    }
  }

  private final String id;
  private final Element elem;

  private volatile Extras extras = null;

  HeiEntryImpl(String id, Element heiElem) {
    this.id = id;
    this.elem = heiElem;
  }

  @Override
  public String getId() {
    return this.id;
  }

  @Override
  public String getName() {
    String bestName = this.getNameEnglish();
    if (bestName != null) {
      return bestName;
    }
    // No English name found. We'll use any name we have.
    bestName = this.getNameNonEnglish();
    if (bestName != null) {
      return bestName;
    }
    // No name at all! Fallback to HEI ID.
    return this.id;
  }

  @Override
  public String getName(String langCode) {
    return this.getExtras().allNames.get(langCode);
  }

  @Override
  public String getNameEnglish() {
    String englishName = this.getExtras().allNames.get("en");
    if (englishName != null) {
      return englishName;
    }
    // No "en" found. Scan for other (less common) English xml:langs.
    // https://github.com/erasmus-without-paper/ewp-registry-client/pull/3#issuecomment-297677150
    for (String s : this.getExtras().allNames.keySet()) {
      if (s.length() >= 2 && s.substring(0, 2).equalsIgnoreCase("en")) {
        return this.getExtras().allNames.get(s);
      }
    }
    // No English xml:langs found.
    return null;
  }

  @Override
  public String getNameNonEnglish() {
    for (String s : this.getExtras().allNames.keySet()) {
      if (!s.toLowerCase(Locale.ROOT).startsWith("en")) {
        return this.getExtras().allNames.get(s);
      }
    }
    return null;
  }

  @Override
  public Collection<String> getOtherIds(String type) {
    List<String> values = this.getExtras().otherIds.get(type);
    if (values == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableCollection(values);
  }

  private Extras getExtras() {
    if (this.extras == null) {
      synchronized (this) {
        if (this.extras == null) {
          this.extras = new Extras(this);
        }
      }
    }
    return this.extras;
  }
}
