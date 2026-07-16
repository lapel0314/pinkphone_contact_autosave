package com.pinkphone.kakaosender;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.ArrayList;

final class ContactSaver {
    private final Context context;

    ContactSaver(Context context) {
        this.context = context;
    }

    String save(Customer customer) throws Exception {
        String phone = customer.phone.trim();
        String name = customer.name.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("고객명이 비어 있습니다.");
        if (phone.isEmpty()) throw new IllegalArgumentException("전화번호가 비어 있습니다.");
        String displayName = displayName(customer);
        ContactRef existing = find(phone);
        if (existing != null) {
            update(existing, displayName, customer.memo.trim());
            return "연락처 업데이트 완료";
        }

        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                .build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build());
        if (!customer.memo.trim().isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, customer.memo.trim())
                    .build());
        }
        context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        return "연락처 저장 완료";
    }

    private void update(ContactRef contact, String displayName, String memo) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues nameValues = new ContentValues();
        nameValues.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName);
        int updatedName = resolver.update(
                ContactsContract.Data.CONTENT_URI,
                nameValues,
                ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                new String[]{String.valueOf(contact.contactId), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}
        );
        if (updatedName == 0 && contact.rawContactId > 0) {
            nameValues.put(ContactsContract.Data.RAW_CONTACT_ID, contact.rawContactId);
            nameValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
            resolver.insert(ContactsContract.Data.CONTENT_URI, nameValues);
        }

        ContentValues noteValues = new ContentValues();
        noteValues.put(ContactsContract.CommonDataKinds.Note.NOTE, memo);
        int updatedNote = resolver.update(
                ContactsContract.Data.CONTENT_URI,
                noteValues,
                ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                new String[]{String.valueOf(contact.contactId), ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE}
        );
        if (updatedNote == 0 && contact.rawContactId > 0 && !memo.isEmpty()) {
            noteValues.put(ContactsContract.Data.RAW_CONTACT_ID, contact.rawContactId);
            noteValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE);
            resolver.insert(ContactsContract.Data.CONTENT_URI, noteValues);
        }
    }

    private ContactRef find(String phone) {
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phone)
        );
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(
                uri,
                new String[]{ContactsContract.PhoneLookup._ID},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            long contactId = cursor.getLong(0);
            return new ContactRef(contactId, rawContactId(contactId));
        }
    }

    private long rawContactId(long contactId) {
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.Data.CONTENT_URI,
                new String[]{ContactsContract.Data.RAW_CONTACT_ID},
                ContactsContract.Data.CONTACT_ID + "=?",
                new String[]{String.valueOf(contactId)},
                null
        )) {
            return cursor != null && cursor.moveToFirst() ? cursor.getLong(0) : -1;
        }
    }

    private String displayName(Customer customer) {
        String date = shortDate(customer.joinDate);
        return date.isEmpty() ? customer.name.trim() : customer.name.trim() + " " + date;
    }

    private String shortDate(String value) {
        if (value == null || value.length() < 10) return "";
        String date = value.substring(0, 10);
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) return "";
        return date.substring(2, 4) + "." + date.substring(5, 7) + "." + date.substring(8, 10);
    }

    private static final class ContactRef {
        final long contactId;
        final long rawContactId;

        ContactRef(long contactId, long rawContactId) {
            this.contactId = contactId;
            this.rawContactId = rawContactId;
        }
    }
}
