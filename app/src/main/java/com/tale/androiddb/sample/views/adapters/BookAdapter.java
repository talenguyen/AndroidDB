package com.tale.androiddb.sample.views.adapters;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tale.androiddb.sample.model.Book;

import tale.androiddb.DatabaseHelper;

/**
 * Created by tale on 11/13/14.
 */
public class BookAdapter extends CursorAdapter {
    private final LayoutInflater layoutInflater;

    public BookAdapter(Context context) {
        super(context, null, true);
        layoutInflater = LayoutInflater.from(context);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return layoutInflater.inflate(android.R.layout.simple_list_item_1, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        Book book = DatabaseHelper.getItem(cursor, Book.class);
        ((TextView) view).setText(book.name);
    }
}
