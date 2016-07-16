package eu.erasmuswithoutpaper.registryclient;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This is a simple {@link Runnable} wrapper that allows it to dynamically schedule <b>a next run of
 * itself</b> (with help of the provided executor).
 */
abstract class SelfSchedulableTask implements Runnable {

  private final ScheduledExecutorService executor;
  private final long minPeriod;

  /**
   * Create a new task.
   *
   * <p>
   * Please note, that this constructor will not schedule the <b>first</b> run. You will need to do
   * it yourself.
   * </p>
   *
   * @param executor an {@link ScheduledExecutorService} to use when scheduling subsequent runs of
   *        this task.
   * @param minPeriod the minimum allowed period between runs, in milliseconds. If the {@link Date}
   *        returned by {@link #runAndScheduleNext()} will be earlier than this, then a later date
   *        will be used in its place (so that it fits the limit specified here).
   */
  SelfSchedulableTask(ScheduledExecutorService executor, long minPeriod) {
    this.executor = executor;
    this.minPeriod = minPeriod;
  }

  @Override
  public final void run() {
    Date nextRun = this.runAndScheduleNext();
    if (nextRun == null) {
      return;
    }
    long delay = nextRun.getTime() - System.currentTimeMillis();
    if (delay < this.minPeriod) {
      delay = this.minPeriod;
    }
    this.executor.schedule(this, delay, TimeUnit.MILLISECONDS);
  }

  /**
   * Run the task and decide when it should be run next time.
   *
   * @return Either a {@link Date} in the future, or <b>null</b> if no subsequent runs should be
   *         scheduled.
   */
  protected abstract Date runAndScheduleNext();
}
