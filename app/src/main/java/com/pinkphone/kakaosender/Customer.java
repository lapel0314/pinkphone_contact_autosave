package com.pinkphone.kakaosender;

import org.json.JSONObject;

final class Customer {
    final String id;
    final String name;
    final String phone;
    final String memo;
    final String joinDate;
    final String carrier;
    final String model;
    final String plan;
    final String addService;

    Customer(JSONObject json) {
        id = json.optString("id", "");
        name = json.optString("name", "");
        phone = json.optString("phone", "");
        memo = json.optString("memo", "");
        joinDate = json.optString("join_date", "");
        carrier = json.optString("carrier", "");
        model = json.optString("model", "");
        plan = json.optString("plan", "");
        addService = json.optString("add_service", "");
    }

    String stableKey() {
        String digits = phone.replaceAll("[^0-9]", "");
        if (!id.isEmpty()) return id;
        if (!digits.isEmpty()) return digits;
        return name;
    }

    String searchText() {
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? name : digits;
    }
}
