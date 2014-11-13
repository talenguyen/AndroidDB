package com.tale.androiddb.sample.model;

import tale.androiddb.Field;
import tale.androiddb.Table;

/**
 * Created by tale on 11/13/14.
 */
@Table
public class Book extends Base {
    @Field
    public String name;
    @Field
    public String description;
}
