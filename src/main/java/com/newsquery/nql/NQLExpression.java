package com.newsquery.nql;

import java.util.List;

public sealed interface NQLExpression permits
        NQLExpression.AndExpr,
        NQLExpression.OrExpr,
        NQLExpression.NotExpr,
        NQLExpression.KeywordExpr,
        NQLExpression.CompareExpr,
        NQLExpression.InExpr {

    record AndExpr(NQLExpression left, NQLExpression right) implements NQLExpression {}
    record OrExpr(NQLExpression left, NQLExpression right) implements NQLExpression {}
    record NotExpr(NQLExpression expr) implements NQLExpression {}
    record KeywordExpr(String text, Double boost) implements NQLExpression {}
    record CompareExpr(String field, String op, String value) implements NQLExpression {}
    record InExpr(String field, List<String> values) implements NQLExpression {}
}
