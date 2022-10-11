Release notes
=============


1.9.0
-----

*Released on 2022-10-11*

* Reduced indexes creation time by walking the DOM


1.8.0
-----

*Released on 2022-03-02*

* Updated Maven plugins
* Updated minimal Maven version to 3.1.1
* Updated Maven Checkstyle Plugin (2.17 -> 3.0.0)
* DefaultCatalogueFetcher: closes inputSteam that is fully read
* DefaultCatalogueFetcher: possible 1 minute timeout for reading registry
* DefaultCatalogueFetcher: no need for special handle of MalformedURLException
* Made use of SLF4J template style logging
* Updated SLF4J (1.7.21 -> 1.7.32)
* Updated findbugs jsr305 (3.0.1 -> 3.0.2)
* Updated test dependencies


1.7.0
-----

*Released on 2021-09-22*

 * Updated project dependencies.
 * Fixed an issue with concurrent access to Element userData field
   that references the original element even after calling clone().
 * Created LICENSE file.


1.6.0
-----

*Released on 2017-12-13*

 * Added support for retrieving RSA public keys for specific API entries. Two
   new methods:

   - `getServerKeyCoveringApi`
   - `getServerKeysCoveringApi`

 * Fixed server-key retrieval in `ClientImpl`. Previously, the
   `isApiCoveredByServerKey` method might have incorrectly returned `false`,
   when it should have returned `true`.

   Also, explicitly stated the `apiEntry` elements retrieved from the
   `RegistryClient` should not be cached.

   See [this thread](https://github.com/erasmus-without-paper/ewp-registry-client/issues/8).


1.5.1
-----

*Released on 2017-10-12*

 * Bugfix: Client would throw an exception if the Registry Service responded
   with a missing `Expires` header (it shouldn't happen in practice, but an
   upgrade is recommended nonetheless).

 * Fixed some minor spelling mistakes in log messages.


1.5.0
-----

*Released on 2017-09-25*

 * Added support for retrieving RSA public keys by their SHA-256 fingerprint
   (the `findRsaPublicKey` method). Key bodies were introduced in [Registry API
   v1.3.0](https://github.com/erasmus-without-paper/ewp-specs-api-registry/blob/v1.3.0/CHANGELOG.md).

 * Added `assertApiIsCoveredByServerKey` method. Since most other `is*` methods
   have their `assert*` counterparts, it seemed better to add this one too.


1.4.0
-----

*Released on 2017-08-22*

 * Added support for new Registry API features, introduced in [Registry API
   v1.2.0](https://github.com/erasmus-without-paper/ewp-specs-api-registry/blob/v1.2.0/CHANGELOG.md).

   New methods were added to `RegistryClient` interface (and its default
   `ClientImpl` implementation):

   - An entire "family" of methods supporting client's RSA keys. These methods
     have very similar signatures to the existing methods which support client
     certificates (and they work in the same way):

     - `areHeisCoveredByClientKey`
     - `assertClientKeyIsKnown`
     - `assertHeiIsCoveredByClientKey`
     - `assertHeisAreCoveredByClientKey`
     - `getHeisCoveredByClientKey`
     - `isClientKeyKnown`
     - `isHeiCoveredByClientKey`

   - A single new method supporting server's RSA keys:

     - `isApiCoveredByServerKey`

 * `ClientImplOptions.getPersistentCacheProvider` was deprecated in favor of
   the newly introduced alias `getPersistentCacheMap`.

 * Minor spelling fixes in the documentation.


1.3.1
-----

*Released on 2017-04-27*

 * Better behavior of most name-related `HeiEntry` methods:

   - `getName()`, `getNameEnglish()` and `getNameNonEnglish()` will now take
     into account the fact that `xml:lang` attributes may contain upper-case
     values, and various subtags and/or extensions.

   - `getName(String langCode)` works as before, but a warning was added in the
     docs, that `langCode` must be an exact match.


1.3.0
-----

*Released on 2017-04-27*

 * Added two convenience methods to `HeiEntry` interface - `getNameEnglish()`
   and `getNameNonEnglish()` (see
   [here](https://github.com/erasmus-without-paper/ewp-registry-client/pull/3)).


1.2.1
-----

*Released on 2016-10-11*

 * Basic XXE prevention (see [here](https://github.com/erasmus-without-paper/ewp-registry-client/issues/2)).


1.2.0
-----

*Released on 2016-10-07*

 * `RegistryClient` interface (and its implementation) has been extended with
   new methods for searching and retrieving HEI attributes (as requested
   [here](https://github.com/erasmus-without-paper/ewp-registry-client/issues/1)).

   The following methods were added (details in javadocs):

   - `HeiEntry findHei(String id)`
   - `HeiEntry findHei(String type, String value)`
   - `Collection<HeiEntry> findHeis(ApiSearchConditions conditions)`
   - `Collection<HeiEntry> getAllHeis()`

 * New `HeiEntry` interface was added.

 * New `setApiClassRequired(namespaceUri, localName, version)` method in
   `ApiSearchConditions` class. This is just a shorthand which allows you to
   call `setApiClassRequired(namespaceUri, localName)` and
   `setMinVersionRequired(version)` both in one call.

 * `ClientImplOptions#getAutoRefreshing()` method has been renamed to
   `isAutoRefreshing()`. The previous name is kept, but deprecated.


1.1.0
-----

*Released on 2016-08-21*

 * `DefaultCatalogueFetcher` has a new constructor which allows it to use an
   alternate Registry Service installation (e.g.
   `dev-registry.erasmuswithoutpaper.eu`, instead of the default
   `registry.erasmuswithoutpaper.eu`).

 * Minor fixes to javadocs: Added missing `@since` tags, fixed some typos,
   extended some of the class and interface descriptions.


1.0.0
-----

*Released on 2016-08-19*

First release of the library. At this time, EWP Network's architecture is still
"in beta", so the structure of the library may still change substantially.
