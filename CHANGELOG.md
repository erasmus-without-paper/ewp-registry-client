Release notes
=============

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
