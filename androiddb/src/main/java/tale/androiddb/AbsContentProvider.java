package tale.androiddb;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import static tale.androiddb.DatabaseHelper.scanUri;

/**
 * Abstract class that extended from {@link android.content.ContentProvider}.
 *
 * @author giangnguyen
 */
public abstract class AbsContentProvider extends ContentProvider {

    private static final String TAG = null;
    private SQLiteOpenHelper mSqliteOpenHelper;


    @Override
    public boolean onCreate() {
        mSqliteOpenHelper = getSQLiteOpenHelper(getContext());
        if (mSqliteOpenHelper == null) {
            throw new NullPointerException("SQLiteOpenHelper must not be null");
        }

        final String authority = getAuthority();
        if (TextUtils.isEmpty(authority)) {
            throw new IllegalArgumentException("authority must not be null");
        }
        DatabaseHelper.setAuthority(authority);
        return true;
    }

    protected abstract SQLiteOpenHelper getSQLiteOpenHelper(Context context);

    protected abstract String getAuthority();

    @Override
    public String getType(Uri uri) {
        final UriMatched uriMatched = scanUri(uri);
        if (uriMatched == null) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        } else {
            final long id = uriMatched.getId();
            final String tableName = uriMatched.getTableName();
            if (id == UriMatched.UNKNOWN_ID) {
                return "vnd.android.cursor.item/" + tableName;
            } else {
                return "vnd.android.cursor.dir/" + tableName;
            }
        }
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        final UriMatched uriMatched = scanUri(uri);
        // Validates the incoming URI. Only the full provider URI is allowed for
        // inserts.
        if (uriMatched == null || uriMatched.getId() != UriMatched.UNKNOWN_ID) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mSqliteOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (ContentValues cv : values) {
                long newID = db.insert(uriMatched.getTableName(), null, cv);

                Log.d(TAG, "new id:" + newID);
            }
            db.setTransactionSuccessful();
            getContext().getContentResolver().notifyChange(uri, null);
            return values.length;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        final UriMatched uriMatched = scanUri(uri);
        int count;
        if (uriMatched == null) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        } else {
            final long id = uriMatched.getId();
            final String tableName = uriMatched.getTableName();
            // Opens the database object in "write" mode.
            final SQLiteDatabase db = mSqliteOpenHelper.getWritableDatabase();
            if (id == UriMatched.UNKNOWN_ID) {
                count = db.delete(tableName, where, whereArgs);
            } else {
                // Performs the delete.
                count = db.delete(tableName, String.format("_id = '%d'", id), null);
            }
        }
        /*
         * Gets a handle to the content resolver object for the current context,
		 * and notifies it that the incoming URI changed. The object passes this
		 * along to the resolver framework, and observers that have registered
		 * themselves for the provider are notified.
		 */
        getContext().getContentResolver().notifyChange(uri, null);
        // Returns the number of rows deleted.
        return count;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final UriMatched uriMatched = scanUri(uri);
        // Validates the incoming URI. Only the full provider URI is allowed for
        // inserts.
        if (uriMatched == null || uriMatched.getId() != UriMatched.UNKNOWN_ID) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // Opens the database object in "write" mode.
        SQLiteDatabase db = mSqliteOpenHelper.getWritableDatabase();
        // Performs the insert and returns the ID of the new row.
        long rowId = db.insert(uriMatched.getTableName(), null, values);
        // If the insert succeeded, the row ID exists.
        if (rowId > 0) {
            // Creates a URI with the item ID pattern and the new row ID
            // appended to it.
            final Uri rowUri = ContentUris.withAppendedId(uri, rowId);
            // Notifies observers registered against this provider that the data
            // changed.
            getContext().getContentResolver().notifyChange(rowUri, null);
            return rowUri;
        }
        // If the insert didn't succeed, then the rowID is <= 0. Throws an
        // exception.
        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        final UriMatched uriMatched = scanUri(uri);
        if (uriMatched == null) {
            // If the URI doesn't match any of the known patterns, throw an
            // exception.
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // Constructs a new query builder and sets its table name
        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(uriMatched.getTableName());
        final long id = uriMatched.getId();
        if (id != UriMatched.UNKNOWN_ID) {
			/*
			 * If the incoming URI is for a single item identified by its ID,
			 * chooses the item ID projection, and appends "_ID = <id>" to the
			 * where clause, so that it selects that single item.
			 */
            qb.appendWhere(String.format("_id = '%d'", id));
        }
        // Opens the database object in "read" mode, since no writes need to be
        // done.
        SQLiteDatabase db = mSqliteOpenHelper.getReadableDatabase();
		/*
		 * Performs the query. If no problems occur trying to read the database,
		 * then a Cursor object is returned; otherwise, the cursor variable
		 * contains null. If no records were selected, then the Cursor object is
		 * empty, and Cursor.getCount() returns 0.
		 */
        Cursor c = qb.query(db, projection, selection, selectionArgs, null,
                null, sortOrder);
        // Tells the Cursor what URI to watch, so it knows when its source data
        // changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where,
                      String[] whereArgs) {
        final UriMatched uriMatched = scanUri(uri);
        if (uriMatched == null) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // Opens the database object in "write" mode.
        final SQLiteDatabase db = mSqliteOpenHelper.getWritableDatabase();
        final long id = uriMatched.getId();
        final String tableName = uriMatched.getTableName();
        int count;
        if (id == UriMatched.UNKNOWN_ID) {
            // Does the update and returns the number of rows updated.
            count = db.update(tableName, // The database table name.
                    values, // A map of column names and new values to use.
                    where, // The where clause column names.
                    whereArgs // The where clause column values to select on.
            );
        } else {
            // Does the update and returns the number of rows updated.
            count = db.update(tableName, values, String.format("_id = '%d'", id),
                    null);
        }
		/*
		 * Gets a handle to the content resolver object for the current context,
		 * and notifies it that the incoming URI changed. The object passes this
		 * along to the resolver framework, and observers that have registered
		 * themselves for the provider are notified.
		 */
        getContext().getContentResolver().notifyChange(uri, null);
        // Returns the number of rows updated.
        return count;
    }

}
