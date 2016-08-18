package eu.erasmuswithoutpaper.registryclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import eu.erasmuswithoutpaper.registryclient.RegistryClient.UnacceptableStalenessException;

import org.junit.Test;
import org.w3c.dom.Element;

public class ClientImplIntegrationTests extends TestBase {

  @Test
  public void test304Responses() {

    /* We need a phaser to prevent ClientImpl from our fetcher in loop. */

    FakeCatalogueFetcher fetcher = new FakeCatalogueFetcher();

    ClientImplOptions options = new ClientImplOptions();
    options.setAutoRefreshing(true);
    options.setMinTimeBetweenQueries(0);
    options.setTimeBetweenRetries(0);
    options.setCatalogueFetcher(fetcher);
    fetcher.setCatalogueToUse("catalogue1.xml");

    fetcher.allowItToContinueOnce();

    try (RegistryClient cli = new ClientImpl(options)) {

      fetcher.waitUntilItCompletes();

      /*
       * Verify that it responded with 200 status and that ClientImpl works.
       */

      assertThat(fetcher.getLastReturnedStatus()).isEqualTo(200);
      Collection<Element> apis = cli.findApis(new ApiSearchConditions());
      assertThat(apis).hasSize(11);

      /*
       * Now, let's wait for ClientImpl to call the fetcher again (it should happen almost
       * immediately because of the reduced limits).
       */

      fetcher.allowItToContinueOnce();
      fetcher.waitUntilItCompletes();

      /*
       * Expect the fetcher to respond with 304 this time, and expect the client to work correctly.
       */

      assertThat(fetcher.getLastReturnedStatus()).isEqualTo(304);
      apis = cli.findApis(new ApiSearchConditions());
      assertThat(apis).hasSize(11);

      // Switch the fetcher to use a different catalogue.

      fetcher.setCatalogueToUse("non-existent.xml");
      fetcher.allowItToContinueOnce();
      fetcher.waitUntilItCompletes();

      // Verify.

      assertThat(fetcher.getLastReturnedStatus()).isNull();
      apis = cli.findApis(new ApiSearchConditions());
      assertThat(apis).hasSize(11);

      // Switch back to the valid catalogue.

      fetcher.setCatalogueToUse("catalogue1.xml");
      fetcher.allowItToContinueOnce();
      fetcher.waitUntilItCompletes();
      assertThat(fetcher.getLastReturnedStatus()).isEqualTo(304);
      apis = cli.findApis(new ApiSearchConditions());
      assertThat(apis).hasSize(11);

      // Switch to a different valid catalogue.

      fetcher.setCatalogueToUse("catalogue2.xml");
      fetcher.allowItToContinueOnce();
      fetcher.waitUntilItCompletes();
      assertThat(fetcher.getLastReturnedStatus()).isEqualTo(200);
      apis = cli.findApis(new ApiSearchConditions());
      assertThat(apis).hasSize(1);

      fetcher.allowItToContinueOnce();
      fetcher.waitUntilItCompletes();
      assertThat(fetcher.getLastReturnedStatus()).isEqualTo(304);
      apis = cli.findApis(new ApiSearchConditions());
      assertThat(apis).hasSize(1);
    }

  }

  @Test
  public void testPersistentCacheUsage() {

    // We will use these fetchers in this test.

    final CatalogueFetcher fetcher1 = new CatalogueFetcher() {

      @Override
      public RegistryResponse fetchCatalogue(String eTag) throws IOException {
        byte[] content = TestBase.getFile("catalogue1.xml");
        String newETag = "catalogue1.xml";
        Date expires = new Date(new Date().getTime() + 300000);
        return new Http200RegistryResponse(content, newETag, expires);
      }
    };
    final CatalogueFetcher fetcher2 = new CatalogueFetcher() {

      @Override
      public RegistryResponse fetchCatalogue(String eTag) throws IOException {
        throw new IOException();
      }
    };

    /*
     * We will use a simple in-memory cache. Note that these options will be reused for many clients
     * below (and we will be changing them in between).
     */

    ClientImplOptions options = new ClientImplOptions();
    options.setAutoRefreshing(true);
    Map<String, byte[]> cache = new HashMap<>();
    options.setPersistentCacheMap(cache);

    // Use a "working" fetcher. Expect it to work.

    options.setCatalogueFetcher(fetcher1);
    try (RegistryClient cli = new ClientImpl(options)) {
      Collection<Element> apis = cli.findApis(new ApiSearchConditions());
      assertThat(apis).hasSize(11);
    }

    // Use an "exception-raising" fetcher. ClientImpl should make use of cache and still work.

    options.setCatalogueFetcher(fetcher2);
    try (RegistryClient cli = new ClientImpl(options)) {
      Collection<Element> apis = cli.findApis(new ApiSearchConditions());
      assertThat(apis).hasSize(11);
    }

    // Break the cache. ClientImpl should stop working, but not throw runtime exceptions.

    for (Entry<String, byte[]> entry : cache.entrySet()) {
      entry.setValue("broken!".getBytes());
    }
    try (RegistryClient cli = new ClientImpl(options)) {
      try {
        cli.findApis(new ApiSearchConditions());
        fail("Exception expected");
      } catch (UnacceptableStalenessException e) {
        // Expected.
      }
    }

    // Use a "working" fetcher with a broken cache. Expect it to work.

    options.setCatalogueFetcher(fetcher1);
    try (RegistryClient cli = new ClientImpl(options)) {
      Collection<Element> apis = cli.findApis(new ApiSearchConditions());
      assertThat(apis).hasSize(11);
    }

    // Verify if the cache was fixed in previous step.

    options.setCatalogueFetcher(fetcher2);
    try (RegistryClient cli = new ClientImpl(options)) {
      Collection<Element> apis = cli.findApis(new ApiSearchConditions());
      assertThat(apis).hasSize(11);
    }

    // Clear the cache. (This should have the same effect as breaking it.)

    cache.clear();
    try (RegistryClient cli = new ClientImpl(options)) {
      try {
        cli.findApis(new ApiSearchConditions());
        fail("Exception expected");
      } catch (UnacceptableStalenessException e) {
        // Expected.
      }
    }
  }

}
