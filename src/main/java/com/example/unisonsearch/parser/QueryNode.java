package com.example.unisonsearch.parser;

/**
 * Abstract base class for all AST nodes in the query tree.
 */
public abstract class QueryNode {

    /**
     * Accept a visitor for traversing the AST.
     * This enables the visitor pattern for SQL generation.
     */
    public abstract <T> T accept(QueryNodeVisitor<T> visitor);

    /**
     * Get a string representation of this node for debugging.
     */
    public abstract String toDebugString();
}
