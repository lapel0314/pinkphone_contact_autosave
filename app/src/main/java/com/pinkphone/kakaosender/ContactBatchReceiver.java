package com.pinkphone.kakaosender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class ContactBatchReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        new Thread(() -> {
            SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
            try {
                ContactBatchRunner.run(context);
            } catch (Exception e) {
                prefs.edit()
                        .putString("contact_batch_last_status", "실패: " + e.getMessage())
                        .apply();
            } finally {
                ContactBatchScheduler.scheduleDaily(context);
                pendingResult.finish();
            }
        }).start();
    }
}
