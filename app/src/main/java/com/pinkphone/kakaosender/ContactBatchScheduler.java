package com.pinkphone.kakaosender;

import android.content.Context;

final class ContactBatchScheduler {
    private ContactBatchScheduler() {}

    static void scheduleDaily(Context context) {
        DailyAlarmScheduler.schedule(context, ContactBatchReceiver.class, 2000, 20, 0);
    }
}
