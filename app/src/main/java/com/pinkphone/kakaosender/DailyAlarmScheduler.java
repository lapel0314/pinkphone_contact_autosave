package com.pinkphone.kakaosender;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDateTime;
import java.time.ZoneId;

final class DailyAlarmScheduler {
    private static final long WINDOW_MILLIS = 10 * 60 * 1000L;

    private DailyAlarmScheduler() {}

    static void schedule(Context context, Class<?> receiverClass, int requestCode, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                nextMillis(hour, minute),
                WINDOW_MILLIS,
                pendingIntent(context, receiverClass, requestCode)
        );
    }

    private static PendingIntent pendingIntent(Context context, Class<?> receiverClass, int requestCode) {
        Intent intent = new Intent(context, receiverClass);
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static long nextMillis(int hour, int minute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
