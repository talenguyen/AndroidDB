package com.tale.androiddb.sample.model.database;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import tale.androiddb.AbsContentProvider;

/**
 * Created by tale on 11/13/14.
 */
public class MyContentProvider extends AbsContentProvider {

    public static final String AUTHORITY = "com.tale.androiddb.sample.provider";
    private static final String DB_NAME = "androiddb_sample";
    private static final int DB_VERSION = 1;

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    protected SQLiteOpenHelper getSQLiteOpenHelper(Context context) {
        return new MySQLiteOpenHelper(context, DB_NAME, DB_VERSION);
    }

    @Override
    protected String getAuthority() {
        return AUTHORITY;
    }
}
