package eu.erasmuswithoutpaper.registryclient;

import static org.assertj.core.api.Assertions.assertThat;

import eu.erasmuswithoutpaper.registryclient.CatalogueDocument;

import org.junit.Test;

public class GenericTests extends TestBase {

  @Test
  public void testReadingResources() {
    assertThat(getFileAsString("file.txt")).isEqualTo("Content.\n");
  }

  @Test
  public void testVersionComparison() {
    assertThat(CatalogueDocument.doesVersionXMatchMinimumRequiredVersionY("1.6.0", "1.10.0"))
        .isFalse();
    assertThat(CatalogueDocument.doesVersionXMatchMinimumRequiredVersionY("1.10.0", "1.6.0"))
        .isTrue();
    assertThat(CatalogueDocument.doesVersionXMatchMinimumRequiredVersionY("1.6.0", "1.6.0"))
        .isTrue();
    assertThat(CatalogueDocument.doesVersionXMatchMinimumRequiredVersionY("1.10", "1.6.0"))
        .isFalse();
    assertThat(CatalogueDocument.doesVersionXMatchMinimumRequiredVersionY("1.10.0", "1.6.0x"))
        .isFalse();
  }
}
