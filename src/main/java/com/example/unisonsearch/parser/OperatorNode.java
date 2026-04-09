package com.example.unisonsearch.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * AST node representing a boolean operator (AND, OR, NOT).
 * Contains child nodes that the operator applies to.
 */
public class OperatorNode extends QueryNode {

    private final OperatorType operator;
    private final List<QueryNode> children;

    public OperatorNode(OperatorType operator) {
        this.operator = operator;
        this.children = new ArrayList<>();
    }

    public OperatorNode(OperatorType operator, List<QueryNode> children) {
        this.operator = operator;
        this.children = new ArrayList<>(children);
    }

    public OperatorType getOperator() {
        return operator;
    }

    public List<QueryNode> getChildren() {
        return children;
    }

    public void addChild(QueryNode child) {
        children.add(child);
    }

    @Override
    public <T> T accept(QueryNodeVisitor<T> visitor) {
        return visitor.visitOperator(this);
    }

    @Override
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(operator.name());
        for (QueryNode child : children) {
            sb.append(" ");
            sb.append(child.toDebugString());
        }
        sb.append(")");
        return sb.toString();
    }
}
