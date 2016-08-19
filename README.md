EWP Registry Client
===================

Java implementation of an **EWP Registry Service** client. Allows you to
discover institutions in the EWP Network, and APIs implemented by them.

 * [What is EWP? What APIs can I search for?][develhub]
 * [What is the purpose of the EWP Registry Service?][registry-intro]

Of course, you are not required to use this client. You can also make use of
[Registry API][registry-api] directly.


Dependencies
------------

Requires **Java 7**. Apart from that, and a tiny [SLF4J](http://slf4j.org/) API
library, *no other dependecies are required*.

The resulting JAR is only `34kB` in size (as of version `1.0.0`).


Installation
------------

Releases are deployed to *Maven Central Repository*. You'll simply need to
include a proper reference in your build's dependencies. Click the image below
for the artifact details.

[![Maven Central]
(https://maven-badges.herokuapp.com/maven-central/eu.erasmuswithoutpaper/ewp-registry-client/badge.svg)]
(https://maven-badges.herokuapp.com/maven-central/eu.erasmuswithoutpaper/ewp-registry-client)


Documentation
-------------

You can browse the project's Javadocs here:

[![Javadocs]
(http://javadoc.io/badge/eu.erasmuswithoutpaper/ewp-registry-client.svg?color=red)]
(http://javadoc.io/doc/eu.erasmuswithoutpaper/ewp-registry-client)


**Where to start?** We define the `RegistryClient` interface, and provide a
single implementation of this interface called `ClientImpl`. Please review
`ClientImpl`'s javadocs for examples of usage.


[develhub]: http://developers.erasmuswithoutpaper.eu/
[registry-intro]: https://github.com/erasmus-without-paper/ewp-specs-architecture#registry
[registry-api]: https://github.com/erasmus-without-paper/ewp-specs-api-registry


Versioning strategy
-------------------

We'll try to use [semantic versioning](http://semver.org/)
(`MAJOR.MINOR.PATCH`) for our release version numbers.

 * **Major version** is incremented when our changes are likely to break your
   builds or runtime behavior.

 * **Minor version** is incremented when new features are added. (Note, that
   such changes still can break your builds, if you have custom implementations
   of the `RegistryClient` interface.)

 * **Patch version** is incremented on bug fixes.


Changelog
---------

See [here](CHANGELOG.md).
