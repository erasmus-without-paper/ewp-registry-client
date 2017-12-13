package eu.erasmuswithoutpaper.registryclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.joox.JOOX.$;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import eu.erasmuswithoutpaper.registryclient.RegistryClient.AssertionFailedException;
import eu.erasmuswithoutpaper.registryclient.RegistryClient.RefreshFailureException;
import eu.erasmuswithoutpaper.registryclient.RegistryClient.UnacceptableStalenessException;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.assertj.core.util.Lists;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;

public class ClientImplBasicTests extends TestBase {

  private static ClientImpl cli;
  private static Certificate cert512;
  private static Certificate cert1024;
  private static Certificate cert1536;
  private static Certificate cert2048;
  private static RSAPublicKey public512;
  private static RSAPublicKey public1024;
  private static RSAPublicKey public1536;
  private static RSAPublicKey public2048;

  @BeforeClass
  public static void setUpClass() {

    // Create a new client. Tell it to use a catalogue from our resources.

    ClientImplOptions options = new ClientImplOptions();
    options.setCatalogueFetcher(new CatalogueFetcher() {
      @Override
      public RegistryResponse fetchCatalogue(String eTag) throws IOException {
        byte[] content = TestBase.getFile("catalogue1.xml");
        String newETag = null;
        Date expires = new Date(new Date().getTime() + 300000);
        return new Http200RegistryResponse(content, newETag, expires);
      }
    });
    cli = new ClientImpl(options);
    try {
      cli.refresh();
    } catch (RefreshFailureException e) {
      throw new RuntimeException(e);
    }

    // Verify that cache age was updated.

    assertThat(cli.getExpiryDate()).isInTheFuture();

    // Load our test certificates.

    cert512 = getCert("cert512.pem");
    cert1024 = getCert("cert1024.pem");
    cert1536 = getCert("cert1536.pem");
    cert2048 = getCert("cert2048.pem");

    // Load our test RSA keys.

    public512 = getPublicKey("public512.pem");
    public1024 = getPublicKey("public1024.pem");
    public1536 = getPublicKey("public1536.pem");
    public2048 = getPublicKey("public2048.pem");
  }

  @AfterClass
  public static void tearDownClass() {
    cli.close();
  }

  @Test
  public void sanityTest() {

    // Test if all certs and keys are loaded properly, and that their fingerprints
    // match what we expect.

    assertThat(Utils.extractFingerprint(cert512))
        .isEqualTo("ac738b33ba7e1347c7859dcbaf519b684fc127084c799259f53022ef1e26186f");
    assertThat(Utils.extractFingerprint(cert1024))
        .isEqualTo("0b9e993d1d4a4e1be879bc5be19c1c0b9073d7bfe1556e015c069c6df6231b7f");
    assertThat(Utils.extractFingerprint(cert1536))
        .isEqualTo("f47643e26f10fd1e5ffe2c933f0a5e6ccf831d789cd80a12720392e90a8f7d42");
    assertThat(Utils.extractFingerprint(cert2048))
        .isEqualTo("19fdd48a85595958035a1a42da8065709a585f78cc01b4df428f68eab39b9dda");

    assertThat(Utils.extractFingerprint(public512))
        .isEqualTo("a29969edd0d04f22bf19db9e417b181c63928accdc58ecf3ac662e51d8497791");
    assertThat(Utils.extractFingerprint(public1024))
        .isEqualTo("4ecc086c841bc8ffa39ea03fa83243e4cda62cd5087e91a3259c09cb2278e15b");
    assertThat(Utils.extractFingerprint(public1536))
        .isEqualTo("eb7ab845698d1294a9f1754f2eaa0975c9d9454f183d99122c71ae9e7acd518e");
    assertThat(Utils.extractFingerprint(public2048))
        .isEqualTo("2e06b1d53a1b7e2c54377d44a4f6893761421e196bb83ad90932d95512c967d1");
  }

  @Test
  public void testAreHeisCoveredByCertOfKey() {
    Collection<String> heiIds = new ArrayList<>();
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1024)).isTrue();
    assertThat(cli.areHeisCoveredByClientKey(heiIds, public1024)).isTrue();
    heiIds.add("john.example.com");
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1024)).isTrue();
    assertThat(cli.areHeisCoveredByClientKey(heiIds, public1024)).isTrue();
    heiIds.add("fred.example.com");
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1024)).isFalse();
    assertThat(cli.areHeisCoveredByClientKey(heiIds, public1024)).isFalse();
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1536)).isTrue();
    assertThat(cli.areHeisCoveredByClientKey(heiIds, public1536)).isTrue();
    heiIds.add("");
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1536)).isFalse();
    assertThat(cli.areHeisCoveredByClientKey(heiIds, public1536)).isFalse();
    heiIds.add(null);
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1536)).isFalse();
    assertThat(cli.areHeisCoveredByClientKey(heiIds, public1536)).isFalse();

    // Overloaded version

    assertThat(cli.areHeisCoveredByCertificate(new String[] {}, cert1024)).isTrue();
    assertThat(cli.areHeisCoveredByClientKey(new String[] {}, public1024)).isTrue();
    assertThat(cli.areHeisCoveredByCertificate(new String[] { "john.example.com" }, cert1024))
        .isTrue();
    assertThat(cli.areHeisCoveredByClientKey(new String[] { "john.example.com" }, public1024))
        .isTrue();
    assertThat(cli.areHeisCoveredByCertificate(
        new String[] { "john.example.com", "fred.example.com" }, cert1024)).isFalse();
    assertThat(cli.areHeisCoveredByClientKey(
        new String[] { "john.example.com", "fred.example.com" }, public1024)).isFalse();
  }

  @Test
  public void testAssertApiIsCoveredByServerKey() {

    // Based on a section of #testIsApiCoveredByServerKey.

    ApiSearchConditions conds = new ApiSearchConditions();
    conds.setApiClassRequired("urn:other", "other-api");
    conds.setMinVersionRequired("1.1.5");
    conds.setRequiredHei("bob.example.com");
    Collection<Element> apis = cli.findApis(conds);
    assertThat(apis).hasSize(1);
    Element api2 = Lists.newArrayList(apis).get(0);

    try {
      cli.assertApiIsCoveredByServerKey(api2, public512);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      assertThat(e).hasMessageContaining("doesn't seem to be covered by this server key");
    }

    try {
      cli.assertApiIsCoveredByServerKey(api2, public1024);
    } catch (AssertionFailedException e) {
      fail("Exception not expected, but thrown.", e);
    }
  }

  @Test
  public void testAssertCertOrKeyIsKnown() {
    try {
      cli.assertCertificateIsKnown(cert512);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      assertThat(e).hasMessageContaining("was not recognized as a known EWP Client");
    }
    try {
      cli.assertClientKeyIsKnown(public512);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      assertThat(e).hasMessageContaining("was not recognized as a known EWP Client");
    }
    try {
      cli.assertCertificateIsKnown(cert1024);
    } catch (AssertionFailedException e) {
      fail("Exception not expected, but thrown.", e);
    }
    try {
      cli.assertClientKeyIsKnown(public1024);
    } catch (AssertionFailedException e) {
      fail("Exception not expected, but thrown.", e);
    }
  }

  @Test
  public void testAssertHeiIsCoveredByCertOrKey() {
    try {
      cli.assertHeiIsCoveredByCertificate("bob.example.com", cert1024);
    } catch (AssertionFailedException e) {
      throw new RuntimeException(e);
    }
    try {
      cli.assertHeiIsCoveredByClientKey("bob.example.com", public1024);
    } catch (AssertionFailedException e) {
      throw new RuntimeException(e);
    }
    try {
      cli.assertHeiIsCoveredByCertificate("fred.example.com", cert1024);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      // Expected.
    }
    try {
      cli.assertHeiIsCoveredByClientKey("fred.example.com", public1024);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      // Expected.
    }
    try {
      cli.assertHeiIsCoveredByCertificate("unknown-hei-id", cert1024);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      // Expected.
    }
    try {
      cli.assertHeiIsCoveredByClientKey("unknown-hei-id", public1024);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      // Expected.
    }
    try {
      cli.assertHeiIsCoveredByCertificate("unknown-hei-id", cert512);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      // Expected.
    }
    try {
      cli.assertHeiIsCoveredByClientKey("unknown-hei-id", public512);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testAssertHeisAreCoveredByCertOrKey() {
    Collection<String> heiIds = new ArrayList<>();
    heiIds.add("john.example.com");
    try {
      cli.assertHeisAreCoveredByCertificate(heiIds, cert1024);
    } catch (AssertionFailedException e) {
      throw new RuntimeException(e);
    }
    try {
      cli.assertHeisAreCoveredByClientKey(heiIds, public1024);
    } catch (AssertionFailedException e) {
      throw new RuntimeException(e);
    }
    heiIds.add("fred.example.com");
    try {
      cli.assertHeisAreCoveredByCertificate(heiIds, cert1024);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      // Expected.
    }
    try {
      cli.assertHeisAreCoveredByClientKey(heiIds, public1024);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      // Expected.
    }

    // Overloaded.

    try {
      cli.assertHeisAreCoveredByCertificate(new String[] { "john.example.com" }, cert1024);
    } catch (AssertionFailedException e) {
      throw new RuntimeException(e);
    }
    try {
      cli.assertHeisAreCoveredByClientKey(new String[] { "john.example.com" }, public1024);
    } catch (AssertionFailedException e) {
      throw new RuntimeException(e);
    }
    try {
      cli.assertHeisAreCoveredByCertificate(new String[] { "john.example.com", "other" }, cert1024);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      // Expected.
    }
    try {
      cli.assertHeisAreCoveredByClientKey(new String[] { "john.example.com", "other" }, public1024);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testFindApi() {
    ApiSearchConditions conds = new ApiSearchConditions();
    String e2 =
        "https://github.com/erasmus-without-paper/ewp-specs-api-echo/blob/stable-v2/manifest-entry.xsd";
    conds.setApiClassRequired(e2, "echo");
    conds.setRequiredHei("bob.example.com");
    Element api = cli.findApi(conds);
    assertThat(api.getNamespaceURI()).isEqualTo(e2);
    assertThat(api.getLocalName()).isEqualTo("echo");
    assertThat(api.getAttribute("version")).isEqualTo("2.1.17");
    conds.setRequiredHei("fred.example.com");
    api = cli.findApi(conds);
    assertThat(api).isNull();
    conds.setRequiredHei("john.example.com");
    conds.setApiClassRequired("urn:other", "other-api");
    api = cli.findApi(conds);
    assertThat(api.getAttribute("version")).isEqualTo("1.1.7");
    api.setAttribute("version", "this change should not influence future calls");
    conds.setMinVersionRequired("1.1.7");
    api = cli.findApi(conds);
    assertThat(api.getAttribute("version")).isEqualTo("1.1.7");
    conds.setMinVersionRequired("1.1.8");
    api = cli.findApi(conds);
    assertThat(api).isNull();
    conds.setApiClassRequired("urn:bla", "standalone2");
    api = cli.findApi(conds);
    assertThat(api).isNull();
    conds.setRequiredHei(null);
    api = cli.findApi(conds);
    assertThat(api.getAttribute("version")).isEqualTo("3.5.7");
    conds.setApiClassRequired("urn:bla", "standalone3");
    api = cli.findApi(conds);
    assertThat(api.getAttribute("version")).isEqualTo("1.2.3");
  }

  @Test
  public void testFindApis() {
    ApiSearchConditions conds = new ApiSearchConditions();
    assertThat(cli.findApis(conds)).hasSize(11);
    conds.setRequiredHei("bob.example.com");
    assertThat(cli.findApis(conds)).hasSize(4);
    conds.setRequiredHei("unknown-hei-id");
    assertThat(cli.findApis(conds)).hasSize(0);
    conds.setRequiredHei(null);
    assertThat(cli.findApis(conds)).hasSize(11);
    String e2 =
        "https://github.com/erasmus-without-paper/ewp-specs-api-echo/blob/stable-v2/manifest-entry.xsd";
    conds.setApiClassRequired(e2, "echo");
    assertThat(cli.findApis(conds)).hasSize(2);
    conds.setApiClassRequired("urn:other", "other-api");
    assertThat(cli.findApis(conds)).hasSize(2);
    conds.setMinVersionRequired("1.1.6");
    assertThat(cli.findApis(conds)).hasSize(1);
    Element api = cli.findApis(conds).iterator().next();
    assertThat($(api).find("url").text()).isEqualTo("https://example.com/super-other");

    // Make sure that changing these values doesn't affect the underlying structures.

    $(api).find("url").text("CHANGED");
    assertThat($(api).find("url").text()).isEqualTo("CHANGED");
    api = cli.findApis(conds).iterator().next();
    assertThat($(api).find("url").text()).isEqualTo("https://example.com/super-other");

    conds.setApiClassRequired(null, null);
    assertThat(cli.findApis(conds)).hasSize(8);
    conds.setApiClassRequired("urn:bla", null);
    assertThat(cli.findApis(conds)).hasSize(2);
    conds.setMinVersionRequired("0");
    assertThat(cli.findApis(conds)).hasSize(0);
    conds.setMinVersionRequired("0.0.0");
    assertThat(cli.findApis(conds)).hasSize(2);
    conds.setMinVersionRequired(null);
    assertThat(cli.findApis(conds)).hasSize(4);
    conds.setRequiredHei("fred.example.com");
    assertThat(cli.findApis(conds)).hasSize(0);

    conds = new ApiSearchConditions();
    conds.setApiClassRequired("urn:bla", "standalone3");
    assertThat(cli.findApis(conds)).hasSize(2);
    conds.setMinVersionRequired("0.0.0");
    assertThat(cli.findApis(conds)).hasSize(1);
    conds.setMinVersionRequired("1.2.4");
    assertThat(cli.findApis(conds)).hasSize(0);
  }

  @Test
  public void testFindHeiById() {
    HeiEntry hei = cli.findHei("bob.example.com");
    assertThat(hei).isNotNull();
    assertThat(hei.getId()).isEqualTo("bob.example.com");
    assertThat(hei.getName()).isIn("Bob's University", "University of the Bob");
    assertThat(hei.getName("en")).isIn("Bob's University", "University of the Bob");
    assertThat(hei.getName("es")).isEqualTo("Universidad de Bob");
    assertThat(hei.getName("pl")).isNull();
    assertThat(hei.getNameEnglish()).isIn("Bob's University", "University of the Bob");
    assertThat(hei.getNameNonEnglish()).isIn("Universidad de Bob");
    assertThat(hei.getOtherIds("erasmus")).containsExactlyInAnyOrder("BOB01");
    assertThat(hei.getOtherIds("previous-schac")).containsExactlyInAnyOrder("bob.com", "bob.org");
    hei = cli.findHei("nonexistent");
    assertThat(hei).isNull();

    // This one has its name only in English.

    hei = cli.findHei("fred.example.com");
    assertThat(hei.getNameEnglish()).isIn("Fred's University");
    assertThat(hei.getNameNonEnglish()).isNull();

    // And this one has a single name with no xml:lang specified.
    // https://github.com/erasmus-without-paper/ewp-registry-client/pull/3#issuecomment-297671815

    hei = cli.findHei("john.example.com");
    assertThat(hei.getNameEnglish()).isNull();
    assertThat(hei.getNameNonEnglish()).isIn("John's University");

    // This one has a name in Spanish only.

    hei = cli.findHei("weird.example.com");
    assertThat(hei.getNameEnglish()).isNull();
    assertThat(hei.getNameNonEnglish()).isIn("Universidad de Fantasmas");
  }

  @Test
  public void testFindHeiByOtherId() {
    HeiEntry hei1 = cli.findHei("bob.example.com");
    HeiEntry hei2 = cli.findHei("previous-schac", "bob.org");
    assertThat(hei1).isNotNull();
    assertThat(hei2).isNotNull();
    assertThat(hei1).isSameAs(hei2);
    assertThat(cli.findHei("nonexistent", "nonexistent")).isNull();
  }

  @Test
  public void testFindHeiId() {
    assertThat(cli.findHeiId("a", "b")).isNull();
    assertThat(cli.findHeiId("erasmus", "BOB01")).isEqualTo("bob.example.com");
    assertThat(cli.findHeiId("erasmus", "bob01")).isEqualTo("bob.example.com");
    assertThat(cli.findHeiId("erasmus", " Bob01 ")).isEqualTo("bob.example.com");
    assertThat(cli.findHeiId("erasmus", " Bob 01 ")).isNull();
    assertThat(cli.findHeiId("erasmus", " Bob02 ")).isNull();
    assertThat(cli.findHeiId("pic", "12346")).isEqualTo("john.example.com");
    assertThat(cli.findHeiId("previous-schac", "bob.com")).isEqualTo("bob.example.com");
    assertThat(cli.findHeiId("previous-schac", "bob.org")).isEqualTo("bob.example.com");
  }

  @Test
  public void testFindHeis() {
    ApiSearchConditions conds = new ApiSearchConditions();
    conds.setApiClassRequired("urn:other", "other-api", "1.1.6");
    assertThat(cli.findHeis(conds)).containsExactlyInAnyOrder(cli.findHei("john.example.com"),
        cli.findHei("fred.example.com"));
    conds.setMinVersionRequired("7.0.0");
    assertThat(cli.findHeis(conds)).isEmpty();
  }

  @Test
  public void testFindRsaPublicKey() {

    // First test a couple of valid ones.

    RSAPublicKey result =
        cli.findRsaPublicKey("a29969edd0d04f22bf19db9e417b181c63928accdc58ecf3ac662e51d8497791");
    assertThat(result).isEqualTo(public512);
    assertThat(result).isNotSameAs(public512);

    result =
        cli.findRsaPublicKey("4ecc086c841bc8ffa39ea03fa83243e4cda62cd5087e91a3259c09cb2278e15b");
    assertThat(result).isEqualTo(public1024);
    assertThat(result).isNotSameAs(public1024);

    // Make sure that the cache works (expect to receive exactly the same RSAPublicKey object).

    RSAPublicKey result2 =
        cli.findRsaPublicKey("4ecc086c841bc8ffa39ea03fa83243e4cda62cd5087e91a3259c09cb2278e15b");
    assertThat(result2).isSameAs(result);

    // This fingerprint exists, but it doesn't contain a valid public key. (In theory, this
    // should never happen, but the client should be prepared for bugs in Registry Service.)

    result =
        cli.findRsaPublicKey("89a5cce39127d8d873b912fdc810b739584d212414b1c78f38b3f9db5973dcc4");
    assertThat(result).isNull();
  }

  @Test
  public void testGetAllHeis() {
    assertThat(cli.getAllHeis()).containsExactlyInAnyOrder(cli.findHei("bob.example.com"),
        cli.findHei("john.example.com"), cli.findHei("fred.example.com"),
        cli.findHei("weird.example.com"));
  }

  @Test
  public void testGetHeisCoveredByCertOrKey() {
    assertThat(cli.getHeisCoveredByCertificate(cert512)).isEmpty();
    assertThat(cli.getHeisCoveredByClientKey(public512)).isEmpty();
    assertThat(cli.getHeisCoveredByCertificate(cert1024))
        .containsExactlyInAnyOrder("john.example.com", "bob.example.com", "weird.example.com");
    assertThat(cli.getHeisCoveredByClientKey(public1024))
        .containsExactlyInAnyOrder("john.example.com", "bob.example.com", "weird.example.com");
    assertThat(cli.getHeisCoveredByCertificate(cert1536))
        .containsExactlyInAnyOrder("john.example.com", "bob.example.com", "fred.example.com");
    assertThat(cli.getHeisCoveredByClientKey(public1536))
        .containsExactlyInAnyOrder("john.example.com", "bob.example.com", "fred.example.com");
    assertThat(cli.getHeisCoveredByCertificate(cert2048))
        .containsExactlyInAnyOrder("bob.example.com");
    assertThat(cli.getHeisCoveredByClientKey(public2048))
        .containsExactlyInAnyOrder("bob.example.com");

    // Try to change it, expect to fail.

    try {
      cli.getHeisCoveredByCertificate(cert2048).add("other");
      fail("Exception expected.");
    } catch (UnsupportedOperationException e) {
      // Expected.
    }
    try {
      cli.getHeisCoveredByClientKey(public2048).add("other");
      fail("Exception expected.");
    } catch (UnsupportedOperationException e) {
      // Expected.
    }
    assertThat(cli.getHeisCoveredByCertificate(cert2048))
        .containsExactlyInAnyOrder("bob.example.com");
    assertThat(cli.getHeisCoveredByClientKey(public2048))
        .containsExactlyInAnyOrder("bob.example.com");
  }

  @Test
  public void testIsCertOrKeyKnown() {
    assertThat(cli.isCertificateKnown(cert512)).isFalse();
    assertThat(cli.isClientKeyKnown(public512)).isFalse();
    assertThat(cli.isCertificateKnown(cert1024)).isTrue();
    assertThat(cli.isClientKeyKnown(public1024)).isTrue();
    assertThat(cli.isCertificateKnown(cert1536)).isTrue();
    assertThat(cli.isClientKeyKnown(public1536)).isTrue();
    assertThat(cli.isCertificateKnown(cert2048)).isTrue();
    assertThat(cli.isClientKeyKnown(public2048)).isTrue();
  }

  @Test
  public void testIsHeiCoveredByCertOrKey() {
    assertThat(cli.isHeiCoveredByCertificate("john.example.com", cert512)).isFalse();
    assertThat(cli.isHeiCoveredByClientKey("john.example.com", public512)).isFalse();
    assertThat(cli.isHeiCoveredByCertificate("john.example.com", cert1024)).isTrue();
    assertThat(cli.isHeiCoveredByClientKey("john.example.com", public1024)).isTrue();
    assertThat(cli.isHeiCoveredByCertificate("bob.example.com", cert1024)).isTrue();
    assertThat(cli.isHeiCoveredByClientKey("bob.example.com", public1024)).isTrue();
    assertThat(cli.isHeiCoveredByCertificate("fred.example.com", cert1024)).isFalse();
    assertThat(cli.isHeiCoveredByClientKey("fred.example.com", public1024)).isFalse();
    assertThat(cli.isHeiCoveredByCertificate("unknown-hei-id", cert1024)).isFalse();
    assertThat(cli.isHeiCoveredByClientKey("unknown-hei-id", public1024)).isFalse();
  }

  @Test
  public void testServerKeyCoverageMethods() {

    // First, find some *specific* API entry elements.

    ApiSearchConditions conds = new ApiSearchConditions();
    String e2 =
        "https://github.com/erasmus-without-paper/ewp-specs-api-echo/blob/stable-v2/manifest-entry.xsd";
    conds.setApiClassRequired(e2, "echo");
    conds.setMinVersionRequired("2.1.3");
    conds.setRequiredHei("bob.example.com");
    Collection<Element> apis = cli.findApis(conds);
    // There are two APIs matching these conditions. We want the 2.1.3 one (just for clarity).
    CollectionUtils.filter(apis, new Predicate<Element>() {
      @Override
      public boolean evaluate(Element obj) {
        return obj.getAttribute("version").equals("2.1.3");
      }
    });
    assertThat(apis).hasSize(1);
    Element api1 = Lists.newArrayList(apis).get(0);

    conds.setApiClassRequired("urn:other", "other-api");
    conds.setMinVersionRequired("1.1.5");
    conds.setRequiredHei("bob.example.com");
    apis = cli.findApis(conds);
    assertThat(apis).hasSize(1);
    Element api2 = Lists.newArrayList(apis).get(0);

    conds.setApiClassRequired("urn:bla", "standalone2");
    conds.setMinVersionRequired("3.5.7");
    conds.setRequiredHei(null);
    apis = cli.findApis(conds);
    assertThat(apis).hasSize(1);
    Element api3 = Lists.newArrayList(apis).get(0);

    conds.setApiClassRequired("urn:other", "other-api");
    conds.setMinVersionRequired("1.1.7");
    conds.setRequiredHei("fred.example.com");
    apis = cli.findApis(conds);
    assertThat(apis).hasSize(1);
    Element api4 = Lists.newArrayList(apis).get(0);

    // All above APIs were marked with a comment in the catalogue file.
    // Test if the results match what is expected (see catalogue file).

    assertThat(cli.isApiCoveredByServerKey(api1, public512)).isTrue();
    assertThat(cli.isApiCoveredByServerKey(api1, public1024)).isFalse();
    assertThat(cli.isApiCoveredByServerKey(api1, public1536)).isFalse();
    assertThat(cli.isApiCoveredByServerKey(api1, public2048)).isFalse();

    assertThat(cli.isApiCoveredByServerKey(api2, public512)).isFalse();
    assertThat(cli.isApiCoveredByServerKey(api2, public1024)).isTrue();
    assertThat(cli.isApiCoveredByServerKey(api2, public1536)).isTrue();
    assertThat(cli.isApiCoveredByServerKey(api2, public2048)).isFalse();

    assertThat(cli.isApiCoveredByServerKey(api3, public512)).isFalse();
    assertThat(cli.isApiCoveredByServerKey(api3, public1024)).isTrue();
    assertThat(cli.isApiCoveredByServerKey(api3, public1536)).isFalse();
    assertThat(cli.isApiCoveredByServerKey(api3, public2048)).isFalse();

    assertThat(cli.isApiCoveredByServerKey(api4, public512)).isFalse();
    assertThat(cli.isApiCoveredByServerKey(api4, public1024)).isFalse();
    assertThat(cli.isApiCoveredByServerKey(api4, public1536)).isFalse();
    assertThat(cli.isApiCoveredByServerKey(api4, public2048)).isFalse();
  }

  @Test
  public void testStalenessDetection() {

    // We will temporarily replace our static client with a stale one.

    ClientImpl previousCli = cli;

    try {

      ClientImplOptions options = new ClientImplOptions();
      options.setCatalogueFetcher(new CatalogueFetcher() {
        @Override
        public RegistryResponse fetchCatalogue(String eTag) throws IOException {
          throw new IOException();
        }
      });
      cli = new ClientImpl(options);

      // Verify it's stale.

      assertThat(cli.getExpiryDate()).isInThePast();

      /*
       * Now, lets call all other tests in this class (with some exceptions) and expect them to
       * throw StaleCacheExceptions.
       *
       * We are using reflection on purpose here. We want to minimize the chance of a developer
       * missing the staleness-check when new methods are added to RegistryClient.
       */

      Set<String> exceptions = new HashSet<>();
      exceptions.add("testStalenessDetection");
      exceptions.add("sanityTest");

      for (Method method : this.getClass().getDeclaredMethods()) {
        if (exceptions.contains(method.getName())) {
          continue;
        }
        if (method.getAnnotation(Test.class) == null) {
          continue;
        }
        try {
          method.invoke(this);
          fail("StaleCacheException expected, but not thrown when calling " + method.getName()
              + " with a stale client.");
        } catch (InvocationTargetException e) {
          if (e.getCause() instanceof UnacceptableStalenessException) {
            // Expected.
          } else {
            fail("StaleCacheException expected, but other exception thrown when calling "
                + method.getName() + " with a stale client.", e);
          }
        } catch (IllegalAccessException | IllegalArgumentException e) {
          throw new RuntimeException(e);
        }
      }

    } finally {
      // Clean up. Return to using the original (not stale) client.
      cli = previousCli;
    }
  }
}
