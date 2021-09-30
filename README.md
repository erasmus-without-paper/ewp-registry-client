EWP Registry Client
===================

Java implementation of an **EWP Registry Service** client.

 * When you're implementing a client, it allows you to **discover
   institutions** in the network, **and APIs** implemented by them.

 * When you're implementing a server, it allows to **verify incoming
   requests** (prove that they come from within the EWP Network, and prove
   which institutions the requester covers).

Of course, you are not required to use this client - you can also make use of
the [Registry API][registry-api] directly. You can also learn more on EWP's
architecture [here][architecture], and get information on all other related
tools and specifications [here][develhub].


Installation and Documentation
------------------------------

Requires **Java 7 SE**. Apart from that, and a tiny [SLF4J](http://slf4j.org/)
API library, *no other dependencies are required*. The resulting JAR is only
`49kB` in size (as of version `1.8.0`).

Releases are deployed to *Maven Central Repository*. You'll simply need to
include a proper reference in your build's dependencies. Click the image below
for the artifact details.

[
    ![Maven Central](https://maven-badges.herokuapp.com/maven-central/eu.erasmuswithoutpaper/ewp-registry-client/badge.svg)
](https://maven-badges.herokuapp.com/maven-central/eu.erasmuswithoutpaper/ewp-registry-client)

You can also browse the project's Javadocs here:

[
    ![Javadocs](http://javadoc.io/badge/eu.erasmuswithoutpaper/ewp-registry-client.svg?color=red)
](http://javadoc.io/doc/eu.erasmuswithoutpaper/ewp-registry-client)

**Where to start?** We define the `RegistryClient` interface, and provide a
single implementation of this interface called `ClientImpl`. Please review
`ClientImpl`'s javadocs for examples of usage.

**Upgrading?** Check out the [changelog (release notes)](CHANGELOG.md).


Versioning strategy
-------------------

We use [semantic versioning](http://semver.org/) (`MAJOR.MINOR.PATCH`) for our
release version numbers.

 * **Major version** is incremented when our changes are likely to break your
   builds or runtime behavior.

 * **Minor version** is incremented when new features are added. (Note, that
   such changes still can break your builds, if you have custom implementations
   of the `RegistryClient` interface.)

 * **Patch version** is incremented on bug fixes, documentation updates, etc.


[develhub]: http://developers.erasmuswithoutpaper.eu/
[architecture]: https://github.com/erasmus-without-paper/ewp-specs-architecture
[registry-api]: https://github.com/erasmus-without-paper/ewp-specs-api-registry
[registry-service]: https://registry.erasmuswithoutpaper.eu/
