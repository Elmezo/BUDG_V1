package com.example.unisonsearch.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses search query strings into an Abstract Syntax Tree (AST).
 * Supports:
 * - Boolean operators: AND, OR, NOT
 * - Parentheses for grouping
 * - Quoted strings: "data quality"
 * - Wildcards: customer*
 * - Field-specific search: name:customer
 * 
 * Operator precedence (highest to lowest):
 * 1. Parentheses
 * 2. NOT
 * 3. AND
 * 4. OR
 */
public class QueryParser {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\"[^\"]*\"|" + // Quoted strings
                    "[a-zA-Z_][a-zA-Z0-9_]*:[^\\s()]+|" + // Field:value
                    "[^\\s()]+|" + // Regular words
                    "[()]" // Parentheses
    );

    private List<Token> tokens;
    private int position;

    /**
     * Parse a query string into an AST.
     * 
     * @param query The search query string
     * @return Root node of the AST
     * @throws QueryParseException if the query has syntax errors
     */
    public QueryNode parse(String query) throws QueryParseException {
        if (query == null || query.trim().isEmpty()) {
            throw new QueryParseException("Query cannot be empty");
        }

        tokens = tokenize(query);
        position = 0;

        if (tokens.isEmpty()) {
            throw new QueryParseException("No valid tokens found in query");
        }

        QueryNode result = parseOrExpression();

        if (position < tokens.size()) {
            throw new QueryParseException("Unexpected tokens after end of query: " + tokens.get(position).value);
        }

        return result;
    }

    /**
     * Tokenize the query string.
     */
    private List<Token> tokenize(String query) {
        List<Token> result = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(query);

        while (matcher.find()) {
            String value = matcher.group();
            TokenType type = determineTokenType(value);
            result.add(new Token(type, value));
        }

        return result;
    }

    /**
     * Determine the type of a token.
     */
    private TokenType determineTokenType(String value) {
        if (value.equals("(")) {
            return TokenType.LPAREN;
        } else if (value.equals(")")) {
            return TokenType.RPAREN;
        } else if (value.equalsIgnoreCase("AND")) {
            return TokenType.AND;
        } else if (value.equalsIgnoreCase("OR")) {
            return TokenType.OR;
        } else if (value.equalsIgnoreCase("NOT")) {
            return TokenType.NOT;
        } else if (value.startsWith("\"") && value.endsWith("\"")) {
            return TokenType.QUOTED_STRING;
        } else if (value.contains(":")) {
            return TokenType.FIELD_TERM;
        } else {
            return TokenType.TERM;
        }
    }

    /**
     * Parse OR expression (lowest precedence).
     * Grammar: orExpr = andExpr (OR andExpr)*
     */
    private QueryNode parseOrExpression() throws QueryParseException {
        QueryNode left = parseAndExpression();

        while (position < tokens.size() && tokens.get(position).type == TokenType.OR) {
            position++; // consume OR
            QueryNode right = parseAndExpression();

            // Create OR node
            OperatorNode orNode = new OperatorNode(OperatorType.OR);
            orNode.addChild(left);
            orNode.addChild(right);
            left = orNode;
        }

        return left;
    }

    /**
     * Parse AND expression (medium precedence).
     * Grammar: andExpr = notExpr (AND notExpr)*
     */
    private QueryNode parseAndExpression() throws QueryParseException {
        QueryNode left = parseNotExpression();

        while (position < tokens.size() && tokens.get(position).type == TokenType.AND) {
            position++; // consume AND
            QueryNode right = parseNotExpression();

            // Create AND node
            OperatorNode andNode = new OperatorNode(OperatorType.AND);
            andNode.addChild(left);
            andNode.addChild(right);
            left = andNode;
        }

        return left;
    }

    /**
     * Parse NOT expression (high precedence).
     * Grammar: notExpr = NOT notExpr | primaryExpr
     */
    private QueryNode parseNotExpression() throws QueryParseException {
        if (position < tokens.size() && tokens.get(position).type == TokenType.NOT) {
            position++; // consume NOT
            QueryNode child = parseNotExpression();

            // Create NOT node
            OperatorNode notNode = new OperatorNode(OperatorType.NOT);
            notNode.addChild(child);
            return notNode;
        }

        return parsePrimaryExpression();
    }

    /**
     * Parse primary expression (highest precedence).
     * Grammar: primaryExpr = LPAREN orExpr RPAREN | term
     */
    private QueryNode parsePrimaryExpression() throws QueryParseException {
        if (position >= tokens.size()) {
            throw new QueryParseException("Unexpected end of query");
        }

        Token token = tokens.get(position);

        // Handle parentheses
        if (token.type == TokenType.LPAREN) {
            position++; // consume (
            QueryNode expr = parseOrExpression();

            if (position >= tokens.size() || tokens.get(position).type != TokenType.RPAREN) {
                throw new QueryParseException("Unmatched opening parenthesis");
            }
            position++; // consume )
            return expr;
        }

        // Handle terms
        if (token.type == TokenType.TERM || token.type == TokenType.QUOTED_STRING
                || token.type == TokenType.FIELD_TERM) {
            position++;
            return createTermNode(token);
        }

        throw new QueryParseException("Unexpected token: " + token.value);
    }

    /**
     * Create a TermNode from a token.
     */
    private TermNode createTermNode(Token token) {
        String value = token.value;
        boolean isQuoted = false;
        boolean hasWildcard = false;
        String field = null;

        // Handle quoted strings
        if (token.type == TokenType.QUOTED_STRING) {
            isQuoted = true;
            value = value.substring(1, value.length() - 1); // Remove quotes
        }

        // Handle field:term
        if (token.type == TokenType.FIELD_TERM) {
            int colonIndex = value.indexOf(':');
            field = value.substring(0, colonIndex);
            value = value.substring(colonIndex + 1);
        }

        // Handle wildcards
        if (value.endsWith("*")) {
            hasWildcard = true;
            value = value.substring(0, value.length() - 1);
        }

        return new TermNode(value, isQuoted, hasWildcard, field);
    }

    /**
     * Token class for lexical analysis.
     */
    private static class Token {
        final TokenType type;
        final String value;

        Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }
    }

    /**
     * Token types.
     */
    private enum TokenType {
        TERM, // Regular word
        QUOTED_STRING, // "quoted phrase"
        FIELD_TERM, // field:value
        AND, // AND operator
        OR, // OR operator
        NOT, // NOT operator
        LPAREN, // (
        RPAREN // )
    }
}
