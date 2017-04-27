package eu.erasmuswithoutpaper.registryclient;

import java.util.Collection;


/**
 * Describes a single HEI entry, as found in the EWP Registry's catalogue.
 *
 * <p>
 * Note, that the EWP Registry keeps only the very import attributes of each HEI (identifiers and
 * name). If you are looking for more information on HEIs, then you should make use of the
 * <a href='https://github.com/erasmus-without-paper/ewp-specs-api-institutions'>Institutions
 * API</a>.
 * </p>
 *
 * @since 1.2.0
 */
public interface HeiEntry {

  /**
   * @return SCHAC ID of this HEI.
   */
  String getId();

  /**
   * Get the name of this HEI.
   *
   * @return We will try to return the name in English. If we cannot find it, we will return the
   *         name in any other language. If we fail this too, we will return the HEI's ID, so you
   *         will never get <code>null</code> here.
   */
  String getName();
  
  /**
   * Get the english name of this HEI.
   *
   * @return We will try to return the name in English. If we cannot find it we will return <code>null</code> here.
   */
  String getEnglishName();
  
  /**
   * Get the other name of this HEI.
   *
   * @return We will try to return the first name not in English. If we cannot find it we will return <code>null</code> here.
   */
  String getOtherName();

  /**
   * Get a name in the given language.
   *
   * @param langCode An ISO 639-1 code of the language (2 lower-case letters).
   * @return String (if the name was found), or null (if it hasn't).
   */
  String getName(String langCode);

  /**
   * Retrieve all <code>&lt;other-id&gt;</code> values of certain type.
   *
   * @param type type identifier, see {@link RegistryClient#findHei(String, String)} for more
   *        information on these types.
   * @return A collection of all matched values for the given type. In no matches were found, an
   *         empty collection will be returned.
   */
  Collection<String> getOtherIds(String type);
}
