package eu.erasmuswithoutpaper.registryclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.joox.JOOX.$;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import eu.erasmuswithoutpaper.registryclient.ApiSearchConditions;
import eu.erasmuswithoutpaper.registryclient.CatalogueFetcher;
import eu.erasmuswithoutpaper.registryclient.ClientImpl;
import eu.erasmuswithoutpaper.registryclient.ClientImplOptions;
import eu.erasmuswithoutpaper.registryclient.RegistryClient.AssertionFailedException;
import eu.erasmuswithoutpaper.registryclient.RegistryClient.RefreshFailureException;
import eu.erasmuswithoutpaper.registryclient.RegistryClient.UnacceptableStalenessException;

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
  }

  @AfterClass
  public static void tearDownClass() {
    cli.close();
  }

  @Test
  public void testAreHeisCoveredByCertificate() {
    Collection<String> heiIds = new ArrayList<>();
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1024)).isTrue();
    heiIds.add("john.example.com");
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1024)).isTrue();
    heiIds.add("fred.example.com");
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1024)).isFalse();
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1536)).isTrue();
    heiIds.add("");
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1536)).isFalse();
    heiIds.add(null);
    assertThat(cli.areHeisCoveredByCertificate(heiIds, cert1536)).isFalse();

    // Overloaded version

    assertThat(cli.areHeisCoveredByCertificate(new String[] {}, cert1024)).isTrue();
    assertThat(cli.areHeisCoveredByCertificate(new String[] { "john.example.com" }, cert1024))
        .isTrue();
    assertThat(cli.areHeisCoveredByCertificate(
        new String[] { "john.example.com", "fred.example.com" }, cert1024)).isFalse();
  }

  @Test
  public void testAssertCertificateIsKnown() {
    try {
      cli.assertCertificateIsKnown(cert512);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      assertThat(e).hasMessageContaining("was not recognized as a known EWP Client");
    }
    try {
      cli.assertCertificateIsKnown(cert1024);
    } catch (AssertionFailedException e) {
      fail("Exception not expected, but thrown.", e);
    }
  }

  @Test
  public void testAssertHeiIsCoveredByCertificate() {
    try {
      cli.assertHeiIsCoveredByCertificate("bob.example.com", cert1024);
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
      cli.assertHeiIsCoveredByCertificate("unknown-hei-id", cert1024);
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
  }

  @Test
  public void testAssertHeisAreCoveredByCertificate() {
    Collection<String> heiIds = new ArrayList<>();
    heiIds.add("john.example.com");
    try {
      cli.assertHeisAreCoveredByCertificate(heiIds, cert1024);
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

    // Overloaded.

    try {
      cli.assertHeisAreCoveredByCertificate(new String[] { "john.example.com" }, cert1024);
    } catch (AssertionFailedException e) {
      throw new RuntimeException(e);
    }
    try {
      cli.assertHeisAreCoveredByCertificate(new String[] { "john.example.com", "other" }, cert1024);
      fail("Exception expected, but not thrown.");
    } catch (AssertionFailedException e) {
      // Expected.
    }
  }

  @Test
  public void testFindApi() {
    ApiSearchConditions conds = new ApiSearchConditions();
    String e1 =
        "https://github.com/erasmus-without-paper/ewp-specs-api-echo/blob/stable-v1/manifest-entry.xsd";
    conds.setApiClassRequired(e1, "echo");
    conds.setRequiredHei("bob.example.com");
    Element api = cli.findApi(conds);
    assertThat(api.getNamespaceURI()).isEqualTo(e1);
    assertThat(api.getLocalName()).isEqualTo("echo");
    assertThat(api.getAttribute("version")).isEqualTo("1.1.17");
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
    String e1 =
        "https://github.com/erasmus-without-paper/ewp-specs-api-echo/blob/stable-v1/manifest-entry.xsd";
    conds.setApiClassRequired(e1, "echo");
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
    assertThat(cli.findApis(conds)).hasSize(6);
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
  }

  @Test
  public void testGetHeisCoveredByCertificate() {
    assertThat(cli.getHeisCoveredByCertificate(cert512)).isEmpty();
    assertThat(cli.getHeisCoveredByCertificate(cert1024))
        .containsExactlyInAnyOrder("john.example.com", "bob.example.com", "weird.example.com");
    assertThat(cli.getHeisCoveredByCertificate(cert1536))
        .containsExactlyInAnyOrder("john.example.com", "bob.example.com", "fred.example.com");
    assertThat(cli.getHeisCoveredByCertificate(cert2048))
        .containsExactlyInAnyOrder("bob.example.com");

    // Try to change it, expect to fail.

    try {
      cli.getHeisCoveredByCertificate(cert2048).add("other");
      fail("Exception expected.");
    } catch (UnsupportedOperationException e) {
      // Expected.
    }
    assertThat(cli.getHeisCoveredByCertificate(cert2048))
        .containsExactlyInAnyOrder("bob.example.com");
  }

  @Test
  public void testIsCertificateKnown() {
    assertThat(cli.isCertificateKnown(cert512)).isFalse();
    assertThat(cli.isCertificateKnown(cert1024)).isTrue();
    assertThat(cli.isCertificateKnown(cert1536)).isTrue();
    assertThat(cli.isCertificateKnown(cert2048)).isTrue();
  }

  @Test
  public void testIsHeiCoveredByCertificate() {
    assertThat(cli.isHeiCoveredByCertificate("john.example.com", cert512)).isFalse();
    assertThat(cli.isHeiCoveredByCertificate("john.example.com", cert1024)).isTrue();
    assertThat(cli.isHeiCoveredByCertificate("bob.example.com", cert1024)).isTrue();
    assertThat(cli.isHeiCoveredByCertificate("fred.example.com", cert1024)).isFalse();
    assertThat(cli.isHeiCoveredByCertificate("unknown-hei-id", cert1024)).isFalse();
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
