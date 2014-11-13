/*
 * Copyright 2014 Giang Nguyen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tale.androiddb;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.TextUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public final class DatabaseHelper {

    private static final Map<Class, TypeAdapter> ADAPTER_MAP = new Hashtable<Class, TypeAdapter>();
    public static final String $$SUFFIX = "_Adapter";
    private static List<TypeAdapter> mAdapters;
    private static String mAuthority;

    /**
     * Set authority for ContentProvider. <NOTE>This method need to be called at first of all if
     * you plan to use ContentProvider</NOTE>
     *
     * @param authority authority value.
     */
    static void setAuthority(String authority) {
        mAuthority = authority;
    }

    public static void createTable(SQLiteDatabase db, Class clazz) {
        final TypeAdapter adapter = getAdapter(clazz);
        db.execSQL(adapter.getCreateStatement());
    }

    static UriMatched scanUri(Uri uri) {
        if (mAdapters == null) {
            mAdapters = getAdapters();
        }
        for (TypeAdapter typeAdapter : getAdapters()) {
            final UriMatched uriMatched = typeAdapter.checkMatched(uri);
            if (uriMatched != null) {
                return uriMatched;
            }
        }
        return null;
    }

    private static List<TypeAdapter> getAdapters() {
        if (ADAPTER_MAP == null || ADAPTER_MAP.size() == 0) {
            return null;
        }
        final List<TypeAdapter> adapters = new ArrayList<TypeAdapter>(ADAPTER_MAP.size());
        for (Class clazz : ADAPTER_MAP.keySet()) {
            final TypeAdapter typeAdapter = ADAPTER_MAP.get(clazz);
            adapters.add(typeAdapter);
        }
        return adapters;
    }

    /**
     * Execute a sql statement to drop the exists table.
     *
     * @param db    SQLiteDatabase object which will execute sql.
     * @param clazz The class map to the table.
     */
    public static void dropTable(SQLiteDatabase db, Class clazz) {
        final TypeAdapter adapter = getAdapter(clazz);
        db.execSQL(String.format("DROP TABLE IF EXISTS %s", adapter.getTableName()));
    }

    /**
     * Insert a object into database.
     *
     * @param helper SQLiteOpenHelper object. Which will be used to open database
     * @param item   The object to be insert.
     * @return The id of the inserted object.
     */
    public static long insert(SQLiteOpenHelper helper, Object item) {
        final SQLiteDatabase database = helper.getWritableDatabase();
        final TypeAdapter adapter = getAdapter(item.getClass());
        return database.insert(adapter.getTableName(), null, adapter.asContentValues(item));
    }

    public static int update(SQLiteOpenHelper helper, Class<?> clazz, ContentValues values, String whereClause, String... whereArgs) {
        final SQLiteDatabase database = helper.getWritableDatabase();
        final TypeAdapter adapter = getAdapter(clazz);
        return database.update(adapter.getTableName(), values, whereClause, whereArgs);
    }

    public static int delete(SQLiteOpenHelper helper, Class<?> clazz, String whereClause, String... whereArgs) {
        final SQLiteDatabase database = helper.getWritableDatabase();
        final TypeAdapter adapter = getAdapter(clazz);
        return database.delete(adapter.getTableName(), whereClause, whereArgs);
    }

    public static Cursor rawQuery(SQLiteOpenHelper helper, String sql, String[] selectionArgs) {
        final SQLiteDatabase database = helper.getReadableDatabase();
        return database.rawQuery(sql, selectionArgs);
    }

    public static <T> List<T> rawQuery(SQLiteOpenHelper helper, Class<? extends T> clazz, String sql, String... selectionArgs) {
        final SQLiteDatabase database = helper.getReadableDatabase();
        final Cursor cursor = database.rawQuery(sql, selectionArgs);
        if (cursor != null) {
            try {
                return getList(cursor, clazz);
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    public static String getTableName(Class<?> clazz) {
        final TypeAdapter adapter = getAdapter(clazz);
        return adapter.getTableName();
    }

    public static Uri getContentUri(Class<?> clazz) {
        return Uri.parse(String.format("content://%s/%s", mAuthority, getTableName(clazz)));
    }

    /**
     * Map an object into a ContentValues object. NOTE: this object must be declared
     * in class declaration.
     *
     * @param item The target item.
     * @return a ContentValues object.
     */
    public static ContentValues toContentValues(Object item) {
        final TypeAdapter adapter = getAdapter(item.getClass());
        return adapter.asContentValues(item);
    }

    /**
     * Create a object of class <b>T</b> and map data from cursor to it.
     *
     * @param cursor The cursor to read data.
     * @param clazz  The target class for object.
     * @return An object of class <b>T</b>
     */
    public static <T> T getItem(Cursor cursor, Class<? extends T> clazz) {
        final TypeAdapter adapter = getAdapter(clazz);
        Object newInstance = adapter.newObject();
        adapter.map(cursor, newInstance);
        return (T) newInstance;
    }

    /**
     * Create a List Object of class <b>T</b> and map data from cursor to it.
     *
     * @param cursor The cursor to read data.
     * @param clazz  The target class for object.
     * @return A List Object of class <b>T</b>
     */
    public static <T> List<T> getList(Cursor cursor, Class<? extends T> clazz) {
        if (cursor.moveToFirst()) {
            final List<T> result = new ArrayList<T>(cursor.getCount());
            final TypeAdapter adapter = getAdapter(clazz);
            do {
                final T newObject = (T) adapter.newObject();
                adapter.map(cursor, newObject);
                result.add(newObject);
            } while (cursor.moveToNext());
            return result;
        }
        return null;
    }

    private static TypeAdapter getAdapter(Class clazz) {
        if (!ADAPTER_MAP.containsKey(clazz)) {
            final TypeAdapter adapter = reflectConstructor(String.format("%s%s", clazz.getName(), $$SUFFIX));
            if (adapter == null) {
                throw new IllegalArgumentException(String.format("Class %s is not supported. Please make sure you have added @Table annotation for that class.", clazz.getName()));
            }
            if (!TextUtils.isEmpty(mAuthority)) {
                adapter.initUriMatcher(mAuthority);
            }
            ADAPTER_MAP.put(clazz, adapter);
            return adapter;
        } else {
            return ADAPTER_MAP.get(clazz);
        }
    }

    private static <T> T reflectConstructor(String className) {
        try {
            final Class<?> clazz = Class.forName(className);
            final Constructor<?> constructor = clazz.getDeclaredConstructor();
            return (T) constructor.newInstance();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
}
