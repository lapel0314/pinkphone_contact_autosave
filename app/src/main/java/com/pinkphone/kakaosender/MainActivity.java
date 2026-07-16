package com.pinkphone.kakaosender;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int CONTACT_PERMISSION_REQUEST = 1001;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Customer> customers = new ArrayList<>();
    private LinearLayout list;
    private TextView status;
    private EditText supabaseUrl;
    private EditText anonKey;
    private EditText email;
    private EditText password;
    private SharedPreferences prefs;
    private Customer pendingContactCustomer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        setContentView(buildUi());
        loadSettings();
        if (!prefs.getString("refresh_token", "").isEmpty()) {
            ContactBatchScheduler.scheduleDaily(this);
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);

        TextView title = text("핑크폰 연락처 자동 저장", 22, true);
        root.addView(title);
        root.addView(text("서버에서 오늘 개통 고객의 이름, 연락처, 메모를 받아 매일 20시에 저장합니다.", 14, false));

        supabaseUrl = input("Supabase URL", false);
        anonKey = input("Supabase anon key", false);
        email = input("이메일", false);
        password = input("비밀번호(저장 안 함)", true);
        root.addView(supabaseUrl);
        root.addView(anonKey);
        root.addView(email);
        root.addView(password);

        LinearLayout buttons = row();
        buttons.addView(button("저장", v -> saveSettings()));
        buttons.addView(button("로그인", v -> login()));
        buttons.addView(button("오늘 대상 조회", v -> fetchCustomers()));
        root.addView(buttons);

        LinearLayout buttons2 = row();
        buttons2.addView(button("연락처 권한", v -> requestContactsPermission()));
        buttons2.addView(button("오늘 연락처 일괄 저장", v -> saveTodayContacts()));
        root.addView(buttons2);

        LinearLayout buttons3 = row();
        buttons3.addView(button("서버 전체 연락처 저장", v -> saveAllContacts()));
        root.addView(buttons3);

        status = text("설정 후 로그인해 주세요.", 14, false);
        status.setPadding(0, dp(10), 0, dp(10));
        root.addView(status);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        return scroll;
    }

    private void loadSettings() {
        supabaseUrl.setText(prefs.getString("supabase_url", ""));
        anonKey.setText(prefs.getString("anon_key", ""));
        email.setText(prefs.getString("email", ""));
    }

    private void saveSettings() {
        prefs.edit()
                .putString("supabase_url", supabaseUrl.getText().toString().trim())
                .putString("anon_key", anonKey.getText().toString().trim())
                .putString("email", email.getText().toString().trim())
                .apply();
        toast("저장했습니다.");
    }

    private void login() {
        saveSettings();
        String passwordText = password.getText().toString();
        runInBackground("로그인 중...", () -> {
            SupabaseClient.LoginResult result = client().login(email.getText().toString().trim(), passwordText);
            prefs.edit()
                    .putString("access_token", result.accessToken)
                    .putString("refresh_token", result.refreshToken)
                    .putString("user_id", result.userId)
                    .apply();
            ContactBatchScheduler.scheduleDaily(this);
            mainHandler.post(() -> fetchCustomers());
        });
    }

    private void fetchCustomers() {
        runInBackground("오늘 연락처 저장 대상 조회 중...", () -> {
            List<Customer> rows = client().fetchContactBatchCustomers();
            mainHandler.post(() -> {
                customers.clear();
                customers.addAll(rows);
                renderCustomers();
                status.setText("오늘 20시 연락처 저장 대상 " + rows.size() + "명");
            });
        });
    }

    private void renderCustomers() {
        list.removeAllViews();
        if (customers.isEmpty()) {
            list.addView(text("조회된 고객이 없습니다.", 15, false));
            return;
        }
        for (Customer customer : customers) {
            list.addView(customerView(customer));
        }
    }

    private View customerView(Customer customer) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        card.setBackgroundColor(0xFFF7F7F7);

        card.addView(text(customer.name + "  " + customer.phone, 17, true));
        card.addView(text("개통일: " + value(customer.joinDate) + " / 모델: " + value(customer.model) + " / 요금제: " + value(customer.plan), 13, false));
        if (!customer.memo.trim().isEmpty()) card.addView(text("메모: " + customer.memo, 13, false));

        LinearLayout first = row();
        first.addView(button("연락처 저장", v -> saveContact(customer)));
        card.addView(first);
        return card;
    }

    private void saveContact(Customer customer) {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingContactCustomer = customer;
            requestContactsPermission();
            return;
        }
        runInBackground("연락처 저장 중...", () -> {
            String result = new ContactSaver(this).save(customer);
            mainHandler.post(() -> toast(result));
        });
    }

    private void requestContactsPermission() {
        requestPermissions(
                new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS},
                CONTACT_PERMISSION_REQUEST
        );
    }

    private void saveTodayContacts() {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestContactsPermission();
            return;
        }
        runInBackground("오늘 개통 고객 연락처 일괄 저장 중...", () -> {
            ContactBatchRunner.Result result = ContactBatchRunner.run(this);
            mainHandler.post(() -> toast("연락처 저장 " + result.saved + "명, 건너뜀 " + result.skipped + "명"));
        });
    }

    private void saveAllContacts() {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestContactsPermission();
            return;
        }
        runInBackground("서버 전체 고객 연락처 저장 중...", () -> {
            ContactBatchRunner.Result result = ContactBatchRunner.runAll(this);
            mainHandler.post(() -> toast("전체 저장 " + result.saved + "명, 건너뜀 " + result.skipped + "명"));
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CONTACT_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && pendingContactCustomer != null) {
            Customer customer = pendingContactCustomer;
            pendingContactCustomer = null;
            saveContact(customer);
        }
    }

    private SupabaseClient client() {
        return new SupabaseClient(
                supabaseUrl.getText().toString().trim(),
                anonKey.getText().toString().trim(),
                prefs.getString("access_token", ""),
                prefs.getString("refresh_token", ""),
                prefs.getString("user_id", "")
        );
    }

    private void runInBackground(String message, ThrowingRunnable runnable) {
        status.setText(message);
        new Thread(() -> {
            try {
                runnable.run();
                mainHandler.post(() -> {
                    if (status.getText().toString().equals(message)) status.setText("완료");
                });
            } catch (Exception e) {
                mainHandler.post(() -> status.setText(e.getMessage()));
            }
        }).start();
    }

    private EditText input(String hint, boolean secret) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setInputType(secret
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        editText.setPadding(0, dp(6), 0, dp(6));
        return editText;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        button.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return button;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(0xFF222222);
        if (bold) textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textView.setPadding(0, dp(3), 0, dp(3));
        return textView;
    }

    private String value(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
