package eu.erasmuswithoutpaper.registryclient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;

import org.w3c.dom.Element;
import org.w3c.dom.Node;


class HeiEntryImpl implements HeiEntry {

  private static class Extras {
    private final String primaryName;
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
      String primaryName = this.allNames.get("en");
      if (primaryName == null) {
        // No English name found. We'll use any name we have.
        Collection<String> names = this.allNames.values();
        if (names.size() > 0) {
          primaryName = names.iterator().next();
        } else {
          // No name at all!
          primaryName = hei.id;
        }
      }
      this.primaryName = primaryName;
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
    return this.getExtras().primaryName;
  }

  @Override
  public String getName(String langCode) {
    return this.getExtras().allNames.get(langCode);
  }

  @Override
  public String getEnglishName()
  {
	  return this.getExtras().allNames.get("en");
  }
  
  @Override
  public String getOtherName()
  {
	  for(String s : this.getExtras().allNames.keySet()){
		  if(!s.equals("en"))
			  return this.getExtras().allNames.get(s);
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
