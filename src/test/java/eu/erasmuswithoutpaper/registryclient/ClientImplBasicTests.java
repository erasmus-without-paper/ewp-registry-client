package eu.erasmuswithoutpaper.registryclient;

import static org.joox.JOOX.$;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import eu.erasmuswithoutpaper.registryclient.RegistryClient.AssertionFailedException;
import eu.erasmuswithoutpaper.registryclient.RegistryClient.RefreshFailureException;
import eu.erasmuswithoutpaper.registryclient.RegistryClient.UnacceptableStalenessException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

  @BeforeAll
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

    assertTrue(cli.getExpiryDate().after(new Date()));

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

  @AfterAll
  public static void tearDownClass() {
    cli.close();
  }

  @Test
  public void sanityTest() {

    // Test if all certs and keys are loaded properly, and that their fingerprints
    // match what we expect.

    assertEquals("ac738b33ba7e1347c7859dcbaf519b684fc127084c799259f53022ef1e26186f",
        Utils.extractFingerprint(cert512));
    assertEquals("0b9e993d1d4a4e1be879bc5be19c1c0b9073d7bfe1556e015c069c6df6231b7f",
        Utils.extractFingerprint(cert1024));
    assertEquals("f47643e26f10fd1e5ffe2c933f0a5e6ccf831d789cd80a12720392e90a8f7d42",
        Utils.extractFingerprint(cert1536));
    assertEquals("19fdd48a85595958035a1a42da8065709a585f78cc01b4df428f68eab39b9dda",
        Utils.extractFingerprint(cert2048));

    assertEquals("a29969edd0d04f22bf19db9e417b181c63928accdc58ecf3ac662e51d8497791",
        Utils.extractFingerprint(public512));
    assertEquals("4ecc086c841bc8ffa39ea03fa83243e4cda62cd5087e91a3259c09cb2278e15b",
        Utils.extractFingerprint(public1024));
    assertEquals("eb7ab845698d1294a9f1754f2eaa0975c9d9454f183d99122c71ae9e7acd518e",
        Utils.extractFingerprint(public1536));
    assertEquals("2e06b1d53a1b7e2c54377d44a4f6893761421e196bb83ad90932d95512c967d1",
        Utils.extractFingerprint(public2048));
  }

  @Test
  public void testAreHeisCoveredByCertOfKey() {
    Collection<String> heiIds = new ArrayList<>();
    assertTrue(cli.areHeisCoveredByCertificate(heiIds, cert1024));
    assertTrue(cli.areHeisCoveredByClientKey(heiIds, public1024));
    heiIds.add("john.example.com");
    assertTrue(cli.areHeisCoveredByCertificate(heiIds, cert1024));
    assertTrue(cli.areHeisCoveredByClientKey(heiIds, public1024));
    heiIds.add("fred.example.com");
    assertFalse(cli.areHeisCoveredByCertificate(heiIds, cert1024));
    assertFalse(cli.areHeisCoveredByClientKey(heiIds, public1024));
    assertTrue(cli.areHeisCoveredByCertificate(heiIds, cert1536));
    assertTrue(cli.areHeisCoveredByClientKey(heiIds, public1536));
    heiIds.add("");
    assertFalse(cli.areHeisCoveredByCertificate(heiIds, cert1536));
    assertFalse(cli.areHeisCoveredByClientKey(heiIds, public1536));
    heiIds.add(null);
    assertFalse(cli.areHeisCoveredByCertificate(heiIds, cert1536));
    assertFalse(cli.areHeisCoveredByClientKey(heiIds, public1536));

    // Overloaded version

    assertTrue(cli.areHeisCoveredByCertificate(new String[] {}, cert1024));
    assertTrue(cli.areHeisCoveredByClientKey(new String[] {}, public1024));
    assertTrue(cli.areHeisCoveredByCertificate(new String[] { "john.example.com" }, cert1024));
    assertTrue(cli.areHeisCoveredByClientKey(new String[] { "john.example.com" }, public1024));
    assertFalse(cli.areHeisCoveredByCertificate(
        new String[] { "john.example.com", "fred.example.com" }, cert1024));
    assertFalse(cli.areHeisCoveredByClientKey(
        new String[] { "john.example.com", "fred.example.com" }, public1024));
  }

  @Test
  public void testAssertApiIsCoveredByServerKey() {

    // Based on a section of #testIsApiCoveredByServerKey.

    ApiSearchConditions conds = new ApiSearchConditions();
    conds.setApiClassRequired("urn:other", "other-api");
    conds.setMinVersionRequired("1.1.5");
    conds.setRequiredHei("bob.example.com");
    Collection<Element> apis = cli.findApis(conds);
    assertEquals(1, apis.size());
    Element api2 = new ArrayList<>(apis).get(0);

    try {
      cli.assertApiIsCoveredByServerKey(api2, public512);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      assertTrue(e.getMessage().contains("doesn't seem to be covered by this server key"));
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
      assertTrue(e.getMessage().contains("was not recognized as a known EWP Client"));
    }
    try {
      cli.assertClientKeyIsKnown(public512);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      assertTrue(e.getMessage().contains("was not recognized as a known EWP Client"));
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
    assertEquals(e2, api.getNamespaceURI());
    assertEquals("echo", api.getLocalName());
    assertEquals("2.1.17", api.getAttribute("version"));
    conds.setRequiredHei("fred.example.com");
    api = cli.findApi(conds);
    assertNull(api);
    conds.setRequiredHei("john.example.com");
    conds.setApiClassRequired("urn:other", "other-api");
    api = cli.findApi(conds);
    assertEquals("1.1.7", api.getAttribute("version"));
    api.setAttribute("version", "this change should not influence future calls");
    conds.setMinVersionRequired("1.1.7");
    api = cli.findApi(conds);
    assertEquals("1.1.7", api.getAttribute("version"));
    conds.setMinVersionRequired("1.1.8");
    api = cli.findApi(conds);
    assertNull(api);
    conds.setApiClassRequired("urn:bla", "standalone2");
    api = cli.findApi(conds);
    assertNull(api);
    conds.setRequiredHei(null);
    api = cli.findApi(conds);
    assertEquals("3.5.7", api.getAttribute("version"));
    conds.setApiClassRequired("urn:bla", "standalone3");
    api = cli.findApi(conds);
    assertEquals("1.2.3", api.getAttribute("version"));
  }

  @Test
  public void testFindApis() {
    ApiSearchConditions conds = new ApiSearchConditions();
    assertEquals(11, cli.findApis(conds).size());
    conds.setRequiredHei("bob.example.com");
    assertEquals(4, cli.findApis(conds).size());
    conds.setRequiredHei("unknown-hei-id");
    assertEquals(0, cli.findApis(conds).size());
    conds.setRequiredHei(null);
    assertEquals(11, cli.findApis(conds).size());
    String e2 =
        "https://github.com/erasmus-without-paper/ewp-specs-api-echo/blob/stable-v2/manifest-entry.xsd";
    conds.setApiClassRequired(e2, "echo");
    assertEquals(2, cli.findApis(conds).size());
    conds.setApiClassRequired("urn:other", "other-api");
    assertEquals(2, cli.findApis(conds).size());
    conds.setMinVersionRequired("1.1.6");
    assertEquals(1, cli.findApis(conds).size());
    Element api = cli.findApis(conds).iterator().next();
    assertEquals("https://example.com/super-other", $(api).find("url").text());

    // Make sure that changing these values doesn't affect the underlying structures.

    $(api).find("url").text("CHANGED");
    assertEquals("CHANGED", $(api).find("url").text());
    api = cli.findApis(conds).iterator().next();
    assertEquals("https://example.com/super-other", $(api).find("url").text());

    conds.setApiClassRequired(null, null);
    assertEquals(8, cli.findApis(conds).size());
    conds.setApiClassRequired("urn:bla", null);
    assertEquals(2, cli.findApis(conds).size());
    conds.setMinVersionRequired("0");
    assertEquals(0, cli.findApis(conds).size());
    conds.setMinVersionRequired("0.0.0");
    assertEquals(2, cli.findApis(conds).size());
    conds.setMinVersionRequired(null);
    assertEquals(4, cli.findApis(conds).size());
    conds.setRequiredHei("fred.example.com");
    assertEquals(0, cli.findApis(conds).size());

    conds = new ApiSearchConditions();
    conds.setApiClassRequired("urn:bla", "standalone3");
    assertEquals(2, cli.findApis(conds).size());
    conds.setMinVersionRequired("0.0.0");
    assertEquals(1, cli.findApis(conds).size());
    conds.setMinVersionRequired("1.2.4");
    assertEquals(0, cli.findApis(conds).size());
  }

  @Test
  public void testFindHeiById() {
    HeiEntry hei = cli.findHei("bob.example.com");
    assertNotNull(hei);
    assertEquals("bob.example.com", hei.getId());
    assertTrue(Arrays.asList("Bob's University", "University of the Bob").contains(hei.getName()));
    assertTrue(Arrays.asList("Bob's University", "University of the Bob").contains(hei.getName("en")));
    assertEquals("Universidad de Bob", hei.getName("es"));
    assertNull(hei.getName("pl"));
    assertTrue(Arrays.asList("Bob's University", "University of the Bob").contains(hei.getNameEnglish()));
    assertTrue(Arrays.asList("Universidad de Bob").contains(hei.getNameNonEnglish()));
    assertEquals(new HashSet<>(Arrays.asList("BOB01")), new HashSet<>(hei.getOtherIds("erasmus")));
    assertEquals(new HashSet<>(Arrays.asList("bob.com", "bob.org")),
        new HashSet<>(hei.getOtherIds("previous-schac")));
    hei = cli.findHei("nonexistent");
    assertNull(hei);

    // This one has its name only in English.

    hei = cli.findHei("fred.example.com");
    assertTrue(Arrays.asList("Fred's University").contains(hei.getNameEnglish()));
    assertNull(hei.getNameNonEnglish());

    // And this one has a single name with no xml:lang specified.
    // https://github.com/erasmus-without-paper/ewp-registry-client/pull/3#issuecomment-297671815

    hei = cli.findHei("john.example.com");
    assertNull(hei.getNameEnglish());
    assertTrue(Arrays.asList("John's University").contains(hei.getNameNonEnglish()));

    // This one has a name in Spanish only.

    hei = cli.findHei("weird.example.com");
    assertNull(hei.getNameEnglish());
    assertTrue(Arrays.asList("Universidad de Fantasmas").contains(hei.getNameNonEnglish()));
  }

  @Test
  public void testFindHeiByOtherId() {
    HeiEntry hei1 = cli.findHei("bob.example.com");
    HeiEntry hei2 = cli.findHei("previous-schac", "bob.org");
    assertNotNull(hei1);
    assertNotNull(hei2);
    assertSame(hei1, hei2);
    assertNull(cli.findHei("nonexistent", "nonexistent"));
  }

  @Test
  public void testFindHeiId() {
    assertNull(cli.findHeiId("a", "b"));
    assertEquals("bob.example.com", cli.findHeiId("erasmus", "BOB01"));
    assertEquals("bob.example.com", cli.findHeiId("erasmus", "bob01"));
    assertEquals("bob.example.com", cli.findHeiId("erasmus", " Bob01 "));
    assertNull(cli.findHeiId("erasmus", " Bob 01 "));
    assertNull(cli.findHeiId("erasmus", " Bob02 "));
    assertEquals("john.example.com", cli.findHeiId("pic", "12346"));
    assertEquals("bob.example.com", cli.findHeiId("previous-schac", "bob.com"));
    assertEquals("bob.example.com", cli.findHeiId("previous-schac", "bob.org"));
  }

  @Test
  public void testFindHeis() {
    ApiSearchConditions conds = new ApiSearchConditions();
    conds.setApiClassRequired("urn:other", "other-api", "1.1.6");
    Collection<HeiEntry> expected = new ArrayList<>();
    expected.add(cli.findHei("john.example.com"));
    expected.add(cli.findHei("fred.example.com"));
    assertEquals(new HashSet<>(expected), new HashSet<>(cli.findHeis(conds)));
    conds.setMinVersionRequired("7.0.0");
    assertTrue(cli.findHeis(conds).isEmpty());
  }

  @Test
  public void testFindRsaPublicKey() {

    // First test a couple of valid ones.

    RSAPublicKey result =
        cli.findRsaPublicKey("a29969edd0d04f22bf19db9e417b181c63928accdc58ecf3ac662e51d8497791");
    assertEquals(public512, result);
    assertNotSame(public512, result);

    result =
        cli.findRsaPublicKey("4ecc086c841bc8ffa39ea03fa83243e4cda62cd5087e91a3259c09cb2278e15b");
    assertEquals(public1024, result);
    assertNotSame(public1024, result);

    // Make sure that the cache works (expect to receive exactly the same RSAPublicKey object).

    RSAPublicKey result2 =
        cli.findRsaPublicKey("4ecc086c841bc8ffa39ea03fa83243e4cda62cd5087e91a3259c09cb2278e15b");
    assertSame(result2, result);

    // This fingerprint exists, but it doesn't contain a valid public key. (In theory, this
    // should never happen, but the client should be prepared for bugs in Registry Service.)

    result =
        cli.findRsaPublicKey("89a5cce39127d8d873b912fdc810b739584d212414b1c78f38b3f9db5973dcc4");
    assertNull(result);
  }

  @Test
  public void testGetAllHeis() {
    Collection<HeiEntry> expected = new ArrayList<>();
    expected.add(cli.findHei("bob.example.com"));
    expected.add(cli.findHei("john.example.com"));
    expected.add(cli.findHei("fred.example.com"));
    expected.add(cli.findHei("weird.example.com"));
    assertEquals(new HashSet<>(expected), new HashSet<>(cli.getAllHeis()));
  }

  @Test
  public void testGetHeisCoveredByCertOrKey() {
    assertTrue(cli.getHeisCoveredByCertificate(cert512).isEmpty());
    assertTrue(cli.getHeisCoveredByClientKey(public512).isEmpty());
    assertEquals(new HashSet<>(Arrays.asList("john.example.com", "bob.example.com", "weird.example.com")),
        new HashSet<>(cli.getHeisCoveredByCertificate(cert1024)));
    assertEquals(new HashSet<>(Arrays.asList("john.example.com", "bob.example.com", "weird.example.com")),
        new HashSet<>(cli.getHeisCoveredByClientKey(public1024)));
    assertEquals(new HashSet<>(Arrays.asList("john.example.com", "bob.example.com", "fred.example.com")),
        new HashSet<>(cli.getHeisCoveredByCertificate(cert1536)));
    assertEquals(new HashSet<>(Arrays.asList("john.example.com", "bob.example.com", "fred.example.com")),
        new HashSet<>(cli.getHeisCoveredByClientKey(public1536)));
    assertEquals(new HashSet<>(Arrays.asList("bob.example.com")),
        new HashSet<>(cli.getHeisCoveredByCertificate(cert2048)));
    assertEquals(new HashSet<>(Arrays.asList("bob.example.com")),
        new HashSet<>(cli.getHeisCoveredByClientKey(public2048)));

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
    assertEquals(new HashSet<>(Arrays.asList("bob.example.com")),
        new HashSet<>(cli.getHeisCoveredByCertificate(cert2048)));
    assertEquals(new HashSet<>(Arrays.asList("bob.example.com")),
        new HashSet<>(cli.getHeisCoveredByClientKey(public2048)));
  }

  @Test
  public void testIsCertOrKeyKnown() {
    assertFalse(cli.isCertificateKnown(cert512));
    assertFalse(cli.isClientKeyKnown(public512));
    assertTrue(cli.isCertificateKnown(cert1024));
    assertTrue(cli.isClientKeyKnown(public1024));
    assertTrue(cli.isCertificateKnown(cert1536));
    assertTrue(cli.isClientKeyKnown(public1536));
    assertTrue(cli.isCertificateKnown(cert2048));
    assertTrue(cli.isClientKeyKnown(public2048));
  }

  @Test
  public void testIsHeiCoveredByCertOrKey() {
    assertFalse(cli.isHeiCoveredByCertificate("john.example.com", cert512));
    assertFalse(cli.isHeiCoveredByClientKey("john.example.com", public512));
    assertTrue(cli.isHeiCoveredByCertificate("john.example.com", cert1024));
    assertTrue(cli.isHeiCoveredByClientKey("john.example.com", public1024));
    assertTrue(cli.isHeiCoveredByCertificate("bob.example.com", cert1024));
    assertTrue(cli.isHeiCoveredByClientKey("bob.example.com", public1024));
    assertFalse(cli.isHeiCoveredByCertificate("fred.example.com", cert1024));
    assertFalse(cli.isHeiCoveredByClientKey("fred.example.com", public1024));
    assertFalse(cli.isHeiCoveredByCertificate("unknown-hei-id", cert1024));
    assertFalse(cli.isHeiCoveredByClientKey("unknown-hei-id", public1024));
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
    apis = apis.stream().filter(obj -> obj.getAttribute("version").equals("2.1.3"))
        .collect(Collectors.toList());
    assertEquals(1, apis.size());
    Element api1 = new ArrayList<>(apis).get(0);

    conds.setApiClassRequired("urn:other", "other-api");
    conds.setMinVersionRequired("1.1.5");
    conds.setRequiredHei("bob.example.com");
    apis = cli.findApis(conds);
    assertEquals(1, apis.size());
    Element api2 = new ArrayList<>(apis).get(0);

    conds.setApiClassRequired("urn:bla", "standalone2");
    conds.setMinVersionRequired("3.5.7");
    conds.setRequiredHei(null);
    apis = cli.findApis(conds);
    assertEquals(1, apis.size());
    Element api3 = new ArrayList<>(apis).get(0);

    conds.setApiClassRequired("urn:other", "other-api");
    conds.setMinVersionRequired("1.1.7");
    conds.setRequiredHei("fred.example.com");
    apis = cli.findApis(conds);
    assertEquals(1, apis.size());
    Element api4 = new ArrayList<>(apis).get(0);

    // All above APIs were marked with a comment in the catalogue file.
    // Test if the results match what is expected (see catalogue file).

    assertTrue(cli.isApiCoveredByServerKey(api1, public512));
    assertFalse(cli.isApiCoveredByServerKey(api1, public1024));
    assertFalse(cli.isApiCoveredByServerKey(api1, public1536));
    assertFalse(cli.isApiCoveredByServerKey(api1, public2048));
    assertEquals(1, cli.getServerKeysCoveringApi(api1).size());
    assertEquals(public512, cli.getServerKeyCoveringApi(api1));

    assertFalse(cli.isApiCoveredByServerKey(api2, public512));
    assertTrue(cli.isApiCoveredByServerKey(api2, public1024));
    assertTrue(cli.isApiCoveredByServerKey(api2, public1536));
    assertFalse(cli.isApiCoveredByServerKey(api2, public2048));
    assertEquals(2, cli.getServerKeysCoveringApi(api2).size());
    assertEquals(new HashSet<>(Arrays.asList(public1024, public1536)),
        new HashSet<>(cli.getServerKeysCoveringApi(api2)));
    assertTrue(Arrays.asList(public1024, public1536).contains(cli.getServerKeyCoveringApi(api2)));

    assertFalse(cli.isApiCoveredByServerKey(api3, public512));
    assertTrue(cli.isApiCoveredByServerKey(api3, public1024));
    assertFalse(cli.isApiCoveredByServerKey(api3, public1536));
    assertFalse(cli.isApiCoveredByServerKey(api3, public2048));
    assertEquals(1, cli.getServerKeysCoveringApi(api3).size());
    assertEquals(public1024, cli.getServerKeyCoveringApi(api3));

    assertFalse(cli.isApiCoveredByServerKey(api4, public512));
    assertFalse(cli.isApiCoveredByServerKey(api4, public1024));
    assertFalse(cli.isApiCoveredByServerKey(api4, public1536));
    assertFalse(cli.isApiCoveredByServerKey(api4, public2048));
    assertEquals(0, cli.getServerKeysCoveringApi(api4).size());
    assertNull(cli.getServerKeyCoveringApi(api4));
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

      assertTrue(cli.getExpiryDate().before(new Date()));

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
