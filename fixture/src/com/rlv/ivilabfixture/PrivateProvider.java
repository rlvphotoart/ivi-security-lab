package com.rlv.ivilabfixture;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class PrivateProvider extends ContentProvider {
    @Override public boolean onCreate() {
        try (FileOutputStream out = getContext().openFileOutput("canary.txt", 0)) {
            out.write("IVI_LAB_HARMLESS_CANARY_v1\n".getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) { return false; }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{"marker"});
        c.addRow(new Object[]{"IVI_LAB_PRIVATE_PROVIDER_v1"});
        return c;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.ivilab.canary"; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
