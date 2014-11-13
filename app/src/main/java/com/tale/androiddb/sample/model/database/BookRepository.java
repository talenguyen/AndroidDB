package com.tale.androiddb.sample.model.database;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;

import com.tale.androiddb.sample.model.Book;

import tale.androiddb.DatabaseHelper;

/**
 * Created by tale on 11/13/14.
 */
public class BookRepository {

    private static final Uri BOOK_URI = DatabaseHelper.getContentUri(Book.class);

    public static long insert(Context context, Book book) {
        final Uri insert = context.getContentResolver().insert(BOOK_URI, DatabaseHelper.toContentValues(book));
        return ContentUris.parseId(insert);
    }

    public static Loader loadBooks(Context context) {
        return new CursorLoader(context, BOOK_URI, null, null, null, null);
    }
}
