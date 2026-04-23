package com.newsquery.nql;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KeywordExtractor {

    public List<NQLExpression.KeywordExpr> extract(NQLExpression expr) {
        var result = new ArrayList<NQLExpression.KeywordExpr>();
        collect(expr, result);
        return result;
    }

    private void collect(NQLExpression expr, List<NQLExpression.KeywordExpr> acc) {
        if (expr instanceof NQLExpression.KeywordExpr kw) {
            acc.add(kw);
        } else if (expr instanceof NQLExpression.AndExpr and) {
            collect(and.left(), acc);
            collect(and.right(), acc);
        } else if (expr instanceof NQLExpression.OrExpr or) {
            collect(or.left(), acc);
            collect(or.right(), acc);
        } else if (expr instanceof NQLExpression.NotExpr not) {
            collect(not.expr(), acc);
        } else if (expr instanceof NQLExpression.AggregationExpr agg) {
            collect(agg.expr(), acc);
        }
        // CompareExpr, InExpr, BetweenExpr — 키워드 없음, 무시
    }
}
