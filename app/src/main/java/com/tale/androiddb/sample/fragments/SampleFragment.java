package com.tale.androiddb.sample.fragments;

import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;

import com.tale.androiddb.sample.R;
import com.tale.androiddb.sample.model.Book;
import com.tale.androiddb.sample.model.database.BookRepository;
import com.tale.androiddb.sample.views.adapters.BookAdapter;

/**
 * Created by tale on 11/13/14.
 */
public class SampleFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private CursorAdapter bookAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sample, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final EditText etBookName = (EditText) view.findViewById(R.id.etBookName);
        ListView lvBooks = (ListView) view.findViewById(R.id.lvBooks);
        view.findViewById(R.id.btAdd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String bookName = etBookName.getText().toString();
                if (TextUtils.isEmpty(bookName)) {
                    etBookName.setError(getString(R.string.error_empty));
                    return;
                }
                addBook(bookName);
            }
        });
        bookAdapter = new BookAdapter(getActivity());
        lvBooks.setAdapter(bookAdapter);
        getLoaderManager().initLoader(1, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        getLoaderManager().restartLoader(1, null, this);
    }

    private void addBook(String bookName) {
        Book book = new Book();
        book.name = bookName;
        BookRepository.insert(getActivity(), book);
    }

    @Override
    public Loader onCreateLoader(int i, Bundle bundle) {
        return BookRepository.loadBooks(getActivity());
    }

    @Override
    public void onLoadFinished(Loader loader, Cursor cursor) {
        bookAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader loader) {
        bookAdapter.swapCursor(null);
    }
}
