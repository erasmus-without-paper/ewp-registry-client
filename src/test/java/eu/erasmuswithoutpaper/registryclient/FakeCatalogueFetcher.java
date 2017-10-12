package eu.erasmuswithoutpaper.registryclient;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
  private CountDownLatch latchNextRunAllowed;
  private CountDownLatch latchPreviousRunCompleted;

  public FakeCatalogueFetcher() {
    this.latchNextRunAllowed = new CountDownLatch(1);
    this.latchPreviousRunCompleted = null;
  }

  public void allowItToContinueOnce() {
    logger.trace("Allowing " + this + " to run");
    this.latchNextRunAllowed.countDown();
  }

  @Override
  public RegistryResponse fetchCatalogue(String eTag) throws IOException {
    if (this.latchPreviousRunCompleted != null) {
      // Notify master that previous loop is completed.
      logger.trace(this + " has completed its previous run.");
      this.latchNextRunAllowed = new CountDownLatch(1);
      this.latchPreviousRunCompleted.countDown();
    }
    this.latchPreviousRunCompleted = new CountDownLatch(1);
    try {
      // Wait for the master to allow another run.
      logger.trace(this + " is waiting for someone to allow it to continue.");
      if (!this.latchNextRunAllowed.await(20, TimeUnit.SECONDS)) {
        throw new RuntimeException("Nobody allowed " + this + " to continue!");
      }
    } catch (InterruptedException e) {
      logger.trace(this + " was interrupted. Will return IOException.");
      this.lastReturnedStatus = null;
      throw new IOException(e);
    }
    logger.trace(this + " was allowed to run.");
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

  public void waitUntilItCompletes() {
    try {
      logger.trace("Waiting for " + this + " to complete its previous run.");
      if (!this.latchPreviousRunCompleted.await(20, TimeUnit.SECONDS)) {
        throw new RuntimeException(this + " failed to notify about completing its previous run!");
      }
      logger.trace("Waking up after " + this + " has completed its previous run.");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
