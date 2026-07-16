package com.pinkphone.kakaosender;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
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
        if (exists(phone)) return "이미 저장된 연락처입니다.";

        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
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

    private boolean exists(String phone) {
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
            return cursor != null && cursor.moveToFirst();
        }
    }
}
