package org.thoughtcrime.securesms.service;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.util.ServiceUtil;

/**
 * Class to help manage scheduling events to happen in the future, whether the app is open or not.
 */
public abstract class TimedEventManager<E> {

  private final Application application;
  private final Handler     handler;

  public TimedEventManager(@NonNull Application application, @NonNull String threadName) {
    HandlerThread handlerThread = new HandlerThread(threadName);
    handlerThread.start();

    this.application = application;
    this.handler     = new Handler(handlerThread.getLooper());

    // XXX Have to delay it because some devices will run the resulting handler#post synchronously,
    //     triggering a method call to an uninitialized child class.
    handler.postDelayed(this::scheduleIfNecessary, 5);
  }

  /**
   * Should be called whenever the underlying data of events has changed. Will appropriately
   * schedule new event executions.
   */
  public void scheduleIfNecessary() {
    handler.removeCallbacksAndMessages(null);

    handler.post(() -> {
      E event = getNextClosestEvent();

      if (event != null) {
        long delay = getDelayForEvent(event);

        handler.postDelayed(() -> {
          executeEvent(event);
          scheduleIfNecessary();
        }, delay);

        scheduleAlarm(application, delay);
      }
    });
  }

  /**
   * @return The next event that should be executed, or {@code null} if there are no events to execute.
   */
  @WorkerThread
  protected @Nullable abstract E getNextClosestEvent();

  /**
   * Execute the provided event.
   */
  @WorkerThread
  protected abstract void executeEvent(@NonNull E event);

  /**
   * @return How long before the provided event should be executed.
   */
  @WorkerThread
  protected abstract long getDelayForEvent(@NonNull E event);

  /**
   * Schedules an alarm to call {@link #scheduleIfNecessary()} after the specified delay. You can
   * use {@link #setAlarm(Context, long, Class)} as a helper method.
   */
  @AnyThread
  protected abstract void scheduleAlarm(@NonNull Application application, long delay);

  /**
   * Helper method to set an alarm.
   */
  protected static void setAlarm(@NonNull Context context, long delay, @NonNull Class alarmClass) {
    Intent        intent        = new Intent(context, alarmClass);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
    AlarmManager  alarmManager  = ServiceUtil.getAlarmManager(context);

    alarmManager.cancel(pendingIntent);
    alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, pendingIntent);
  }
}
