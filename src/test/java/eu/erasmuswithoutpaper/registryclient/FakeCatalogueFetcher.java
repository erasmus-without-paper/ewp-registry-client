package eu.erasmuswithoutpaper.registryclient;

import java.io.IOException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A "puppet" {@link CatalogueFetcher} implementations which can be (somewhat) controlled from other
 * threads.
 *
 * @see ClientImplIntegrationTests#test304Responses() Example of how it's used
 */
public class FakeCatalogueFetcher implements CatalogueFetcher {

  private static final Logger logger = LoggerFactory.getLogger(FakeCatalogueFetcher.class);

  private volatile String catalogueToUse;
  private volatile Integer lastReturnedStatus;

  @Override
  public RegistryResponse fetchCatalogue(String eTag) throws IOException {
    this.lastReturnedStatus = null;
    String newETag = this.catalogueToUse;

    // Set it to expire immediately.

    Date expires = new Date();

    // Which response should we return?

    if (newETag.equals(eTag)) {
      this.lastReturnedStatus = 304;
      logger.trace(this + " is returning 304.");
      return new Http304RegistryResponse(expires);
    } else {
      byte[] content = TestBase.getPossiblyNonExistingFile(this.catalogueToUse);
      this.lastReturnedStatus = 200;
      logger.trace(this + " is returning 200.");
      return new Http200RegistryResponse(content, newETag, expires);
    }
  }

  public Integer getLastReturnedStatus() {
    return this.lastReturnedStatus;
  }

  public void setCatalogueToUse(String filename) {
    this.catalogueToUse = filename;
  }

  @Override
  public String toString() {
    return "FakeCatalogueFetcher[" + this.catalogueToUse + "]";
  }
}
