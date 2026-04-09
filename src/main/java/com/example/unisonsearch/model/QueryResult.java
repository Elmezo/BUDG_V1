package com.example.unisonsearch.model;

import java.util.List;

/**
 * Holds the final SQL string and ordered parameters for the prepared statement.
 */
public class QueryResult {
    public final String sql;
    public final List<Object> parameters;

    public QueryResult(String sql, List<Object> parameters) {
        this.sql = sql;
        this.parameters = parameters;
    }
}


