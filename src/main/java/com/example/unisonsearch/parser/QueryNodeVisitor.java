package com.example.unisonsearch.parser;

/**
 * Visitor interface for traversing the query AST.
 * Used for SQL generation and other AST operations.
 */
public interface QueryNodeVisitor<T> {

    T visitTerm(TermNode node);

    T visitOperator(OperatorNode node);
}
