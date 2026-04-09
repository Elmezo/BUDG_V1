package com.example.unisonsearch.model;

/**
 * Represents a single search condition with operator, module, and query.
 */
public class SearchCondition {
    public final String operator; // FIND, AND, OR, NOT
    public final String module;
    public final String query;

    public SearchCondition(String operator, String module, String query) {
        this.operator = operator != null ? operator.toUpperCase() : "FIND";
        this.module = module;
        this.query = query;
    }
}

