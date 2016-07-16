package eu.erasmuswithoutpaper.registryclient;

import java.util.Map;

import eu.erasmuswithoutpaper.registryclient.RegistryClient.UnacceptableStalenessException;

/**
 * Options passed to {@link ClientImpl} on construction.
 */
public class ClientImplOptions {

  private CatalogueFetcher catalogueFetcher;
  private long maxAcceptableStaleness;
  private boolean autoRefreshing;
  private Map<String, byte[]> persistentCacheMap;
  private long minTimeBetweenQueries;
  private long timeBetweenRetries;

  /**
   * Create a new set of options, initialized with default values.
   *
   * <p>
   * Use <code>set*</code> methods to change the defaults. Default values are mentioned in the
   * documentation of each method. Note, that each of the <code>set*</code> methods returns
   * <b>this</b>, to enable setter chaining.
   * </p>
   */
  public ClientImplOptions() {
    this.catalogueFetcher = new DefaultCatalogueFetcher();
    this.maxAcceptableStaleness = 5 * 86400000;
    this.autoRefreshing = false;
    this.persistentCacheMap = null;
    this.minTimeBetweenQueries = 60000;
    this.timeBetweenRetries = 180000;
  }

  public boolean getAutoRefreshing() {
    return this.autoRefreshing;
  }

  public CatalogueFetcher getCatalogueFetcher() {
    return this.catalogueFetcher;
  }

  public long getMaxAcceptableStaleness() {
    return this.maxAcceptableStaleness;
  }

  public long getMinTimeBetweenQueries() {
    return this.minTimeBetweenQueries;
  }

  public Map<String, byte[]> getPersistentCacheProvider() {
    return this.persistentCacheMap;
  }

  /**
   * Return the minimum staleness of the catalogue, above which {@link ClientImpl} will begin to
   * report warning-level messages in its logs.
   *
   * @return milliseconds
   */
  public long getStalenessWarningThreshold() {
    return Math.min(this.getMaxAcceptableStaleness() / 4, 86400000);
  }

  public long getTimeBetweenRetries() {
    return this.timeBetweenRetries;
  }

  /**
   * Set auto-refreshing on or off. Default is off (to avoid accidental "DoS attacks" on
   * misconfiguration), but it is recommended to turn it on.
   *
   * <p>
   * Once this option is turned on, {@link ClientImpl} will automatically call its
   * {@link ClientImpl#refresh()} method whenever the currently held version of the catalogue
   * document expires. This will usually be done in a separate background thread, but
   * {@link ClientImpl} might also try to do this during the construction time, if it thinks it's
   * needed.
   * </p>
   *
   * @param autoRefreshing <b>true</b> to turn auto-refreshing on, <b>false</b> to turn it off.
   * @return This object.
   */
  public ClientImplOptions setAutoRefreshing(boolean autoRefreshing) {
    this.autoRefreshing = autoRefreshing;
    return this;
  }

  /**
   * Tell {@link ClientImpl} to use a custom {@link CatalogueFetcher}.
   *
   * <p>
   * By default, it will use a {@link DefaultCatalogueFetcher} instance for fetching the catalogue
   * files from the Registry Service. You might want to tweak this is some situations (for example
   * when running unit-tests).
   * </p>
   *
   * @param catalogueFetcher {@link CatalogueFetcher} to use.
   * @return This object.
   */
  public ClientImplOptions setCatalogueFetcher(CatalogueFetcher catalogueFetcher) {
    if (catalogueFetcher == null) {
      throw new IllegalArgumentException();
    }
    this.catalogueFetcher = catalogueFetcher;
    return this;
  }

  /**
   * Set a limit on maximum allowed staleness of the used catalogue. Default is 5 days.
   *
   * <p>
   * In general, if auto-refreshing is turned on, {@link ClientImpl} will always keep the internal
   * copy of the catalogue up-to-date. However, if {@link ClientImpl} will not be able to fetch a
   * fresh copy, then it will reuse the older (stale) copy of the catalogue. The amount of time
   * which has passed since the internal copy has expired is defined as "catalogue staleness".
   * </p>
   *
   * <p>
   * It is reasonable to to use a copy of a catalogue which is one or two hours stale, if - for some
   * reason - we cannot retrieve the fresh copy. However, as the staleness grows, it becomes more
   * dangerous to use such stale copy. Once the staleness reaches a certain limit,
   * {@link ClientImpl} will start to throw {@link UnacceptableStalenessException} exceptions. These
   * are not checked exceptions, and they are not usually caught, so, most likely, they will cause
   * your application to break. Note, that {@link ClientImpl} will attempt to warn you <b>before</b>
   * this happens (see {@link #getStalenessWarningThreshold()}).
   * </p>
   *
   * @param milliseconds the new value of acceptable staleness, in milliseconds.
   * @return This object.
   */
  public ClientImplOptions setMaxAcceptableStaleness(long milliseconds) {
    this.maxAcceptableStaleness = milliseconds;
    return this;
  }

  /**
   * Set a <b>minimum</b> time between subsequent auto-refresh queries. Default is 60 seconds.
   * Usually there is no need to change this default. It is relevant only in unit-tests.
   *
   * <p>
   * In auto-refreshing mode {@link ClientImpl} attempts to query the Registry Service as often as
   * <b>the Registry Service</b> wishes. For example: If the Registry tells us to refresh in 15
   * minutes, then we will refresh in 15 minutes. However, if the Registry tells us to refresh in 1
   * second (which might be due to invalid {@link CatalogueFetcher} implementation), then
   * {@link ClientImpl} will <b>not</b> listen to such suggestion, and use the value supplied here
   * instead.
   * </p>
   *
   * <p>
   * This option is relevant only in auto-refreshing mode. Manual calls to
   * {@link ClientImpl#refresh()} will never be limited.
   * </p>
   *
   * @param milliseconds the minimum time between auto-refresh queries, in milliseconds.
   * @return This object.
   */
  public ClientImplOptions setMinTimeBetweenQueries(long milliseconds) {
    this.minTimeBetweenQueries = milliseconds;
    return this;
  }

  /**
   * Tell {@link ClientImpl} to use a given map as persistent cache between its runs. Default is
   * <b>null</b>.
   *
   * <p>
   * If given, {@link ClientImpl} will use this map to keep a persistent copy of the catalogue. It
   * will load the catalogue from this cache during construction. If the loaded copy is fresh
   * enough, then it may speed up the construction time and help you avoid
   * {@link UnacceptableStalenessException} exceptions. It is okay for this cache to be purged
   * without notice, it is also okay for some elements to be removed once other stay. You should
   * however take care that all of its keys are modifiable by {@link ClientImpl} only.
   * </p>
   *
   * <p>
   * In most cases, supplying this object makes sense only if its data is persisted to a hard drive,
   * or a similar "more persistent than usual" medium (otherwise you can simply keep
   * {@link ClientImpl} in memory instead of destroying it between runs).
   * </p>
   *
   * @param persistentCacheMap a new map to use as cache.
   * @return This object.
   */
  public ClientImplOptions setPersistentCacheMap(Map<String, byte[]> persistentCacheMap) {
    this.persistentCacheMap = persistentCacheMap;
    return this;
  }

  /**
   * Set a time between query retries, when no valid response was received. Default is 3 minutes.
   *
   * <p>
   * This option is relevant only in auto-refreshing mode. If the catalogue cannot be retrieved for
   * some reason, this is the time we will wait before we retry.
   * </p>
   *
   * @param milliseconds time, in milliseconds.
   * @return This object.
   */
  public ClientImplOptions setTimeBetweenRetries(long milliseconds) {
    this.timeBetweenRetries = milliseconds;
    return this;
  }

  @Override
  public String toString() {
    return "ClientImplOptions [catalogueFetcher=" + this.catalogueFetcher
        + ", maxAcceptableStaleness=" + this.maxAcceptableStaleness + ", autoRefreshing="
        + this.autoRefreshing + ", persistentCacheProvider=" + this.persistentCacheMap + "]";
  }
}
