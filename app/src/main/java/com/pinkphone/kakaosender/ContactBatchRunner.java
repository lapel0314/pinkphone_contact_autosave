package com.pinkphone.kakaosender;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.List;

final class ContactBatchRunner {
    private ContactBatchRunner() {}

    static Result run(Context context) throws Exception {
        if (context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("연락처 권한이 없습니다.");
        }
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        SupabaseClient client = new SupabaseClient(
                prefs.getString("supabase_url", ""),
                prefs.getString("anon_key", ""),
                prefs.getString("access_token", ""),
                prefs.getString("refresh_token", ""),
                prefs.getString("user_id", "")
        );
        SupabaseClient.LoginResult session = client.refreshSession();
        prefs.edit()
                .putString("access_token", session.accessToken)
                .putString("refresh_token", session.refreshToken)
                .putString("user_id", session.userId)
                .apply();

        List<Customer> customers = client.fetchContactBatchCustomers();
        ContactSaver saver = new ContactSaver(context);
        int saved = 0;
        int skipped = 0;
        for (Customer customer : customers) {
            try {
                String result = saver.save(customer);
                if (result.contains("완료")) saved++;
                else skipped++;
            } catch (Exception e) {
                skipped++;
            }
        }
        prefs.edit()
                .putString("contact_batch_last_status", "연락처 저장 " + saved + "명, 건너뜀 " + skipped + "명")
                .apply();
        return new Result(saved, skipped);
    }

    static final class Result {
        final int saved;
        final int skipped;

        Result(int saved, int skipped) {
            this.saved = saved;
            this.skipped = skipped;
        }
    }
}
