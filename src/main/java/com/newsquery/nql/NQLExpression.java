package com.newsquery.nql;

import java.util.List;
import java.util.Optional;

public sealed interface NQLExpression permits
        NQLExpression.AndExpr,
        NQLExpression.OrExpr,
        NQLExpression.NotExpr,
        NQLExpression.KeywordExpr,
        NQLExpression.CompareExpr,
        NQLExpression.InExpr,
        NQLExpression.BetweenExpr,
        NQLExpression.MatchAllExpr,
        NQLExpression.AggregationExpr {

    record AndExpr(NQLExpression left, NQLExpression right) implements NQLExpression {}
    record OrExpr(NQLExpression left, NQLExpression right) implements NQLExpression {}
    record NotExpr(NQLExpression expr) implements NQLExpression {}
    record KeywordExpr(String text, Double boost) implements NQLExpression {}
    record CompareExpr(String field, String op, String value) implements NQLExpression {}
    record InExpr(String field, List<String> values) implements NQLExpression {}
    record BetweenExpr(String field, String start, String end) implements NQLExpression {}
    record MatchAllExpr() implements NQLExpression {}
    record AggregationExpr(
        NQLExpression expr,
        String groupByField,
        Optional<Integer> limit
    ) implements NQLExpression {}
}
