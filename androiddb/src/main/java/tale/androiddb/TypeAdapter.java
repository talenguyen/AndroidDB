package tale.androiddb;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * Created by TALE on 10/20/2014.
 */
public interface TypeAdapter<T> {

    String getCreateStatement();

    String getTableName();

    void map(Cursor cursor, T target);

    T newObject();

    ContentValues asContentValues(T target);

    void initUriMatcher(String authority);

    UriMatched checkMatched(Uri uri);
}
