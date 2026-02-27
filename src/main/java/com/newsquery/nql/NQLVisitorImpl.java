package com.newsquery.nql;

import java.util.List;
import java.util.stream.Collectors;

public class NQLVisitorImpl extends NQLBaseVisitor<NQLExpression> {

    @Override
    public NQLExpression visitQuery(NQLParser.QueryContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public NQLExpression visitAndExpr(NQLParser.AndExprContext ctx) {
        return new NQLExpression.AndExpr(visit(ctx.expr(0)), visit(ctx.expr(1)));
    }

    @Override
    public NQLExpression visitOrExpr(NQLParser.OrExprContext ctx) {
        return new NQLExpression.OrExpr(visit(ctx.expr(0)), visit(ctx.expr(1)));
    }

    @Override
    public NQLExpression visitNotExpr(NQLParser.NotExprContext ctx) {
        return new NQLExpression.NotExpr(visit(ctx.expr()));
    }

    @Override
    public NQLExpression visitGroupExpr(NQLParser.GroupExprContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public NQLExpression visitMatchAllExpr(NQLParser.MatchAllExprContext ctx) {
        return new NQLExpression.MatchAllExpr();
    }

    @Override
    public NQLExpression visitKwExpr(NQLParser.KwExprContext ctx) {
        return visit(ctx.keywordExpr());
    }

    @Override
    public NQLExpression visitKeywordExpr(NQLParser.KeywordExprContext ctx) {
        String text = stripQuotes(ctx.STRING().getText());
        Double boost = ctx.NUMBER() != null
            ? Double.parseDouble(ctx.NUMBER().getText())
            : null;
        return new NQLExpression.KeywordExpr(text, boost);
    }

    @Override
    public NQLExpression visitFieldExprRule(NQLParser.FieldExprRuleContext ctx) {
        return visit(ctx.fieldExpr());
    }

    @Override
    public NQLExpression visitFieldExpr(NQLParser.FieldExprContext ctx) {
        String field = ctx.field().getText();
        if (ctx.IN() != null) {
            List<String> values = ctx.valueList().value().stream()
                .map(v -> stripQuotes(v.getText()))
                .collect(Collectors.toList());
            return new NQLExpression.InExpr(field, values);
        } else {
            String op = ctx.compOp().getText();
            String value = stripQuotes(ctx.value().getText());
            return new NQLExpression.CompareExpr(field, op, value);
        }
    }

    private String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
