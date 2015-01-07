package androiddb.internal;


import com.squareup.javawriter.JavaWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Writer;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.lang.model.element.Element;

import tale.androiddb.TypeAdapter;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

class AdapterObject {

    private static final String SERIALIZE_FUNCTION = "serialize";
    private static final String DESERIALIZE_FUNCTION = "deserialize";

    /**
     * Used as a template to create a new table
     */
    private static final String CREATE_TABLE_DEFAULT = "CREATE TABLE %s "
            + "(%s INTEGER PRIMARY KEY AUTOINCREMENT, %s);";

    /**
     * SQL statement to select the id of the last inserted row. Does not end with ; in order to be
     * used with SQLiteDatabase#rawQuery(String, String[])
     */
    private static final String GET_ID_OF_LAST_INSERTED_ROW_SQL
            = "SELECT ROWID FROM %s ORDER BY ROWID DESC LIMIT 1";

    private final Element element;
    private final String classPackage;
    private final String className;
    private final ProcessorLogger logger;

    private boolean isChildTable = false;
    private String idColumnName;

    private final List<TableColumn> columns = new LinkedList<TableColumn>();

    AdapterObject(Element element, String classPackage, String className, ProcessorLogger logger) {
        this.element = element;
        this.classPackage = classPackage;
        this.className = className;
        this.logger = logger;
    }

    void setIdColumnName(String idColumnName) {
        this.idColumnName = idColumnName;
    }

    String getIdColumnName() {
        return idColumnName;
    }

    void setIsChildTable(boolean isChildTable) {
        this.isChildTable = isChildTable;
    }

    Element getOriginatingElement() {
        return element;
    }

    void addColumn(TableColumn column) {
        columns.add(column);
    }

    String getTableName() {
        return element.getSimpleName().toString().replace(".", "_");
    }

    String getTargetClass() {
        return element.toString();
    }

    /**
     * Get table schema
     */
    private String getSchema() {
        StringBuilder sb = new StringBuilder();
        Iterator<TableColumn> iterator = columns.iterator();
        while (iterator.hasNext()) {
            TableColumn column = iterator.next();
            if (column.isOneToMany()) {
                if (!iterator.hasNext()) {
                    // remove the extra ", " after one to many
                    int length = sb.length();
                    sb.replace(length - 2, length, "");
                }
                continue;
            }
            sb.append(column);
            if (iterator.hasNext()) {
                sb.append(", ");
            }
        }

        return String.format(CREATE_TABLE_DEFAULT, getTableName(), idColumnName, sb.toString());
    }

    /**
     * Get the fully qualified class name
     */
    String getFqcn() {
        return classPackage + "." + className;
    }

    /**
     * Create the java functions required for the internal class
     */
    void brewJava(Writer writer) throws IOException {
        logger.d("brewJava");
        JavaWriter javaWriter = new JavaWriter(writer);
        javaWriter.setCompressingTypes(false);

        javaWriter.emitSingleLineComment("Generated code from Shillelagh. Do not modify!")
                .emitPackage(classPackage)
        /* Knows nothing of android types */
                .emitImports("android.content.ContentValues",
                        "android.database.Cursor",
                        "android.database.DatabaseUtils",
                        "android.database.sqlite.SQLiteDatabase",
                        "android.net.Uri",
                        "android.content.UriMatcher",
                        "android.content.ContentUris",
                        "tale.androiddb.UriMatched")
                .emitImports(ByteArrayInputStream.class, ByteArrayOutputStream.class, IOException.class,
                        ObjectInputStream.class, ObjectOutputStream.class, LinkedList.class, Date.class,
                        List.class)
                .beginType(className, "class", EnumSet.of(PUBLIC, FINAL), null, String.format("%s<%s>", TypeAdapter.class.getName(), getTargetClass()))
                .emitField("int", "SINGLE_ITEM", EnumSet.of(PRIVATE, STATIC, FINAL), "1")
                .emitField("int", "MULTIPLE_ITEM", EnumSet.of(PRIVATE, STATIC, FINAL), "2")
                .emitField("UriMatcher", "mUriMatcher", EnumSet.of(PRIVATE))
                .emitField("String", "mAuthority", EnumSet.of(PRIVATE));


        // Implement TypeAdapter
        emitGetCreateStatement(javaWriter);
        emitNewObject(javaWriter);
        emitAsContentValues(javaWriter);
        emitMapCursor(javaWriter);
        emitGetTableName(javaWriter);
        emitUriMatched(javaWriter);
        emitInitUriMatched(javaWriter);
        emitByteArraySerialization(javaWriter);
        javaWriter.endType();
    }

    private void emitInitUriMatched(JavaWriter javaWriter) throws IOException {

        logger.d("emitInitUriMatched");
        javaWriter.beginMethod("void", "initUriMatcher", EnumSet.of(PUBLIC), "String", "authority")
                .emitStatement("this.mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH)")
                .emitStatement("this.mUriMatcher.addURI(authority, getTableName(), MULTIPLE_ITEM)")
                .emitStatement("this.mUriMatcher.addURI(authority, getTableName()+\"/#\", SINGLE_ITEM)")
                .endMethod();

//        public void initProvider(String authority) {
//            mAuthority = authority;
//            final String tableName = getTableName();
//            mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
//            mUriMatcher.addURI(mAuthority, tableName, MULTIPLE_ITEM);
//            mUriMatcher.addURI(mAuthority, tableName + "/#", SINGLE_ITEM);
//        }
    }

    private void emitUriMatched(JavaWriter javaWriter) throws IOException {
        logger.d("emitUriMatched");
        javaWriter.beginMethod(
                "UriMatched", "checkMatched", EnumSet.of(PUBLIC), "Uri", "uri")
                .emitStatement("final String tableName = getTableName()")
                .emitStatement("final int type = mUriMatcher.match(uri)")
                .beginControlFlow("if (type == SINGLE_ITEM)")
                .emitStatement("final long id = ContentUris.parseId(uri)")
                .emitStatement("return new UriMatched(id, tableName)")
                .nextControlFlow("else if (type == MULTIPLE_ITEM)")
                .emitStatement("return new UriMatched(UriMatched.UNKNOWN_ID, tableName)")
                .nextControlFlow("else")
                .emitStatement("return null")
                .endControlFlow()
                .endMethod();
    }

    /**
     * Creates the function for creating the table
     */
    private void emitGetCreateStatement(JavaWriter javaWriter) throws IOException {
        logger.d("emitGetCreateStatement");
        javaWriter.beginMethod(
                "String", "getCreateStatement", EnumSet.of(PUBLIC))
                .emitStatement("return \"%s\"", getSchema())
                .endMethod();
    }

    private void emitNewObject(JavaWriter javaWriter) throws IOException {
        logger.d("emitNewObject");
        javaWriter.beginMethod(
                getTargetClass(), "newObject", EnumSet.of(PUBLIC))
                .emitStatement("return new %s()", getTargetClass())
                .endMethod();
    }

    /**
     * Creates the function dropping the table
     */
    private void emitGetTableName(JavaWriter javaWriter) throws IOException {
        logger.d("emitGetTableName");
        javaWriter.beginMethod(
                "String", "getTableName", EnumSet.of(PUBLIC))
                .emitStatement("return \"%s\"", getTableName())
                .endMethod();
    }

    /**
     * Creates the function for inserting a new value into the database
     */
    private void emitAsContentValues(JavaWriter javaWriter) throws IOException {
        logger.d("emitAsContentValues");
        String tableName = getTableName();
        javaWriter.beginMethod("ContentValues", "asContentValues", EnumSet.of(PUBLIC),
                getTargetClass(), "target")
                .emitStatement("ContentValues values = new ContentValues()");
        List<TableColumn> childColumns = new LinkedList<TableColumn>();
        for (TableColumn column : columns) {
            String columnName = column.getColumnName();
            if (column.isBlob() && !column.isByteArray()) {
                javaWriter.emitStatement("values.put(\"%s\", %s(target.%s))", columnName,
                        SERIALIZE_FUNCTION, columnName);
            } else if (column.isOneToOne()) {
//        javaWriter.emitStatement("%s%s.%s(target.%s, db)", column.getType(),
//            $$SUFFIX, INSERT_ONE_TO_ONE, column.getColumnName())
//            .emitStatement("values.put(\"%s\", %s%s.%s(target.%s))", columnName,
//                column.getType(), $$SUFFIX, GET_ID_FUNCTION, columnName);
            } else if (column.isDate()) {
                javaWriter.emitStatement(
                        "values.put(\"%s\", target.%s.getTime())", columnName, columnName);
            } else if (column.isOneToMany()) {
//        childColumns.add(column);
            } else if (!column.isOneToManyChild()) {
                javaWriter.emitStatement("values.put(\"%s\", target.%s)", columnName, columnName);
            }
        }
        javaWriter.emitStatement("return %s", "values");
        javaWriter.endMethod();
    }


    private void emitMapCursor(JavaWriter javaWriter) throws IOException {
        javaWriter.beginMethod("void", "map", EnumSet.of(PUBLIC),
                "Cursor", "cursor", getTargetClass(), "target")
                .emitStatement("target.%s = cursor.getLong(cursor.getColumnIndex(\"%s\"))",
                        idColumnName, idColumnName);

        for (TableColumn column : columns) {
            String columnName = column.getColumnName();
            if (column.isDate()) {
                javaWriter.emitStatement(
                        "target.%s = new Date(cursor.%s(cursor.getColumnIndex(\"%s\")))", columnName,
                        CursorFunctions.get(long.class.getName()), columnName);
            } else if (column.isOneToOne()) {
//                javaWriter.emitStatement(
//                        "target.%s = %s%s.%s(cursor.%s(cursor.getColumnIndex(\"%s\")), db)",
//                        columnName, column.getType(), $$SUFFIX, $$GET_OBJECT_BY_ID,
//                        CursorFunctions.get(Long.class.getName()), columnName);
            } else if (column.isBoolean()) {
                javaWriter.emitStatement("target.%s = cursor.%s(cursor.getColumnIndex(\"%s\")) == 1",
                        columnName, CursorFunctions.get(column.getType()), columnName);
            } else if (column.getSqlType() == SqliteType.BLOB) {
                if (column.isByteArray()) {
                    javaWriter.emitStatement("target.%s = cursor.%s(cursor.getColumnIndex(\"%s\"))",
                            columnName, CursorFunctions.get(column.getType()), columnName);
                } else {
                    javaWriter.emitStatement(
                            "target.%s = %s(cursor.%s(cursor.getColumnIndex(\"%s\")));", columnName,
                            DESERIALIZE_FUNCTION, CursorFunctions.get(column.getType()), columnName);
                }
            } else if (column.isOneToMany()) {
//                javaWriter.emitStatement("Cursor childCursor = %s%s.%s(db)", column.getType(),
//                        $$SUFFIX, SELECT_ALL_FUNCTION)
//                        .emitStatement("target.%s = %s%s.%s(childCursor, db)",
//                                column.getColumnName(), column.getType(), $$SUFFIX, $$MAP_OBJECT_FUNCTION)
//                        .emitStatement("childCursor.close()");
            } else if (column.isOneToManyChild()) {
                // TODO Skip and have custom mapping?
            } else {
                javaWriter.emitStatement("target.%s = cursor.%s(cursor.getColumnIndex(\"%s\"))",
                        columnName, CursorFunctions.get(column.getType()), columnName);
            }
        }

        javaWriter.endMethod();
    }

    /**
     * Creates functions for serialization to and from byte arrays
     */
    private void emitByteArraySerialization(JavaWriter javaWriter) throws IOException {
        logger.d("emitByteArraySerialization");
        javaWriter.beginMethod("<K> byte[]", SERIALIZE_FUNCTION, EnumSet.of(STATIC), "K", "object")
                .beginControlFlow("try")
                .emitStatement("ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()")
                .emitStatement(
                        "ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)")
                .emitStatement("objectOutputStream.writeObject(object)")
                .emitStatement("return byteArrayOutputStream.toByteArray()")
                .nextControlFlow("catch (IOException e)")
                .emitStatement("throw new RuntimeException(e)")
                .endControlFlow()
                .endMethod()
                .beginMethod("<K> K", DESERIALIZE_FUNCTION, EnumSet.of(STATIC), "byte[]", "bytes")
                .beginControlFlow("try")
                .emitStatement(
                        "ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes)")
                .emitStatement(
                        "ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)")
                .emitStatement("return (K) objectInputStream.readObject()")
                .nextControlFlow("catch (IOException e)")
                .emitStatement("throw new RuntimeException(e)")
                .nextControlFlow("catch (ClassNotFoundException e)")
                .emitStatement("throw new RuntimeException(e)")
                .endControlFlow()
                .endMethod();
    }

    @Override
    public String toString() {
        return getSchema();
    }
}
