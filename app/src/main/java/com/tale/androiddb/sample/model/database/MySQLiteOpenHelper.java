package com.tale.androiddb.sample.model.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.tale.androiddb.sample.model.Book;

import tale.androiddb.DatabaseHelper;

/**
 * Created by tale on 11/13/14.
 */
public class MySQLiteOpenHelper extends SQLiteOpenHelper {

    public MySQLiteOpenHelper(Context context, String name, int version) {
        super(context, name, null, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        DatabaseHelper.createTable(db, Book.class);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        DatabaseHelper.dropTable(db, Book.class);
        onCreate(db);
    }
}
