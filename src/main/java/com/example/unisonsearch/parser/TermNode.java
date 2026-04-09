package com.example.unisonsearch.parser;

/**
 * AST node representing a search term.
 * Can be a simple word, quoted phrase, wildcard, or field-specific search.
 */
public class TermNode extends QueryNode {

    private final String term;
    private final boolean isQuoted;
    private final boolean hasWildcard;
    private final String field; // Optional: name, description, etc.

    public TermNode(String term, boolean isQuoted, boolean hasWildcard, String field) {
        this.term = term;
        this.isQuoted = isQuoted;
        this.hasWildcard = hasWildcard;
        this.field = field;
    }

    public TermNode(String term) {
        this(term, false, false, null);
    }

    public String getTerm() {
        return term;
    }

    public boolean isQuoted() {
        return isQuoted;
    }

    public boolean hasWildcard() {
        return hasWildcard;
    }

    public String getField() {
        return field;
    }

    @Override
    public <T> T accept(QueryNodeVisitor<T> visitor) {
        return visitor.visitTerm(this);
    }

    @Override
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        if (field != null) {
            sb.append(field).append(":");
        }
        if (isQuoted) {
            sb.append("\"").append(term).append("\"");
        } else {
            sb.append(term);
        }
        if (hasWildcard) {
            sb.append("*");
        }
        return sb.toString();
    }
}
