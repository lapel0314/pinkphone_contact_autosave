package com.pinkphone.kakaosender;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class SupabaseClient {
    private final String baseUrl;
    private final String anonKey;
    private String accessToken;
    private String refreshToken;
    private String userId;

    SupabaseClient(String baseUrl, String anonKey, String accessToken, String refreshToken, String userId) {
        this.baseUrl = stripSlash(baseUrl);
        this.anonKey = anonKey;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
    }

    LoginResult login(String email, String password) throws Exception {
        JSONObject body = new JSONObject()
                .put("email", email)
                .put("password", password);
        JSONObject json = requestJson(
                "POST",
                "/auth/v1/token?grant_type=password",
                body.toString(),
                false
        );
        accessToken = json.optString("access_token", "");
        refreshToken = json.optString("refresh_token", "");
        JSONObject user = json.optJSONObject("user");
        userId = user == null ? "" : user.optString("id", "");
        if (accessToken.isEmpty()) throw new IllegalStateException("Supabase access token을 받지 못했습니다.");
        return new LoginResult(accessToken, refreshToken, userId);
    }

    LoginResult refreshSession() throws Exception {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new IllegalStateException("Supabase refresh token이 없습니다. 앱에서 다시 로그인해 주세요.");
        }
        JSONObject body = new JSONObject().put("refresh_token", refreshToken);
        JSONObject json = requestJson(
                "POST",
                "/auth/v1/token?grant_type=refresh_token",
                body.toString(),
                false
        );
        accessToken = json.optString("access_token", "");
        refreshToken = json.optString("refresh_token", refreshToken);
        JSONObject user = json.optJSONObject("user");
        userId = user == null ? userId : user.optString("id", userId);
        if (accessToken.isEmpty()) throw new IllegalStateException("Supabase access token 갱신 실패");
        return new LoginResult(accessToken, refreshToken, userId);
    }

    List<Customer> fetchContactBatchCustomers() throws Exception {
        return fetchCustomersForDate(LocalDate.now());
    }

    private List<Customer> fetchCustomersForDate(LocalDate targetDate) throws Exception {
        String select = "id,name,phone,memo,join_date,carrier,model,plan,add_service";
        String path = "/rest/v1/customers"
                + "?select=" + enc(select)
                + "&is_deleted=eq.false"
                + "&join_date=gte." + enc(targetDate + "T00:00:00")
                + "&join_date=lt." + enc(targetDate.plusDays(1) + "T00:00:00")
                + "&order=join_date.asc";
        JSONArray array = requestArray("GET", path, null);
        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            customers.add(new Customer(array.getJSONObject(i)));
        }
        return customers;
    }

    private JSONObject requestJson(String method, String path, String body, boolean preferMinimal) throws Exception {
        return new JSONObject(requestText(method, path, body, preferMinimal));
    }

    private JSONArray requestArray(String method, String path, String body) throws Exception {
        return new JSONArray(requestText(method, path, body, false));
    }

    private String requestText(String method, String path, String body, boolean preferMinimal) throws Exception {
        if (baseUrl.isEmpty() || anonKey.isEmpty()) {
            throw new IllegalStateException("Supabase URL과 anon key를 먼저 입력해 주세요.");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("apikey", anonKey);
        connection.setRequestProperty("Authorization", "Bearer " + (accessToken == null || accessToken.isEmpty() ? anonKey : accessToken));
        connection.setRequestProperty("Content-Type", "application/json");
        if (preferMinimal) connection.setRequestProperty("Prefer", "return=minimal");
        if (body != null) {
            connection.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
        }
        int code = connection.getResponseCode();
        String text = read(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Supabase HTTP " + code + ": " + text);
        }
        return text.isEmpty() ? "{}" : text;
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            return builder.toString();
        }
    }

    private static String stripSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    static final class LoginResult {
        final String accessToken;
        final String refreshToken;
        final String userId;

        LoginResult(String accessToken, String refreshToken, String userId) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
        }
    }
}
