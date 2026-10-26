package eu.erasmuswithoutpaper.registryclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import eu.erasmuswithoutpaper.registryclient.RegistryClient.RefreshFailureException;

import org.junit.jupiter.api.Test;

public class GenericTests extends TestBase {

  @Test
  public void testReadingResources() {
    assertEquals("Content.", getFileAsString("file.txt").trim());
  }

  @Test
  public void testVersionComparison() {
    assertFalse(CatalogueDocument.doesVersionXMatchMinimumRequiredVersionY("1.6.0", "1.10.0"));
    assertTrue(CatalogueDocument.doesVersionXMatchMinimumRequiredVersionY("1.10.0", "1.6.0"));
    assertTrue(CatalogueDocument.doesVersionXMatchMinimumRequiredVersionY("1.6.0", "1.6.0"));
    assertFalse(CatalogueDocument.doesVersionXMatchMinimumRequiredVersionY("1.10", "1.6.0"));
    assertFalse(CatalogueDocument.doesVersionXMatchMinimumRequiredVersionY("1.10.0", "1.6.0x"));
  }

  @Test
  public void testXXE() {
    FakeCatalogueFetcher fetcher = new FakeCatalogueFetcher();
    ClientImplOptions options = new ClientImplOptions();
    options.setCatalogueFetcher(fetcher);
    fetcher.setCatalogueToUse("xxe.xml");
    try (RegistryClient cli = new ClientImpl(options)) {
      cli.refresh();
      fail("Exception expected.");
    } catch (RefreshFailureException e) {
      // The rest of the parser's message is localized.
      assertTrue(e.getCause().getCause().getMessage()
          .contains("http://apache.org/xml/features/disallow-doctype-decl"));
    }
  }
}
