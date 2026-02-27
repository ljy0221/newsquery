package com.newsquery.query;

import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsquery.nql.NQLExpression;
import com.newsquery.nql.NQLExpression.*;

import java.util.Map;

public class ESQueryBuilder {

    private static final Map<String, String> RANGE_OPS = Map.of(
        ">", "gt", ">=", "gte", "<", "lt", "<=", "lte"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    public ObjectNode build(NQLExpression expr) {
        if (expr instanceof AndExpr and) {
            return buildAnd(and);
        } else if (expr instanceof OrExpr or) {
            return buildOr(or);
        } else if (expr instanceof NotExpr not) {
            return buildNot(not);
        } else if (expr instanceof KeywordExpr kw) {
            return buildKeyword(kw);
        } else if (expr instanceof CompareExpr cmp) {
            return buildCompare(cmp);
        } else if (expr instanceof InExpr in) {
            return buildIn(in);
        } else if (expr instanceof MatchAllExpr) {
            return buildMatchAll();
        }
        throw new IllegalArgumentException("Unknown expression type: " + expr.getClass());
    }

    private ObjectNode buildKeyword(KeywordExpr kw) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode mm = root.putObject("multi_match");
        mm.put("query", kw.text());
        mm.putArray("fields").add("title^2").add("content");
        if (kw.boost() != null) {
            mm.put("boost", kw.boost());
        }
        return root;
    }

    private ObjectNode buildCompare(CompareExpr cmp) {
        if (cmp.op().equals("==")) {
            ObjectNode root = mapper.createObjectNode();
            root.putObject("term").put(cmp.field(), cmp.value());
            return root;
        }
        if (cmp.op().equals("!=")) {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode mustNot = root.putObject("bool").putArray("must_not");
            ObjectNode term = mapper.createObjectNode();
            term.putObject("term").put(cmp.field(), cmp.value());
            mustNot.add(term);
            return root;
        }
        String rangeOp = RANGE_OPS.get(cmp.op());
        if (rangeOp == null) {
            throw new IllegalArgumentException("지원하지 않는 비교 연산자: " + cmp.op());
        }
        ObjectNode root = mapper.createObjectNode();
        ObjectNode rangeField = root.putObject("range").putObject(cmp.field());
        if (cmp.field().equals("score")) {
            rangeField.put(rangeOp, Double.parseDouble(cmp.value()));
        } else {
            rangeField.put(rangeOp, cmp.value());
        }
        return root;
    }

    private ObjectNode buildIn(InExpr in) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode values = root.putObject("terms").putArray(in.field());
        in.values().forEach(values::add);
        return root;
    }

    private ObjectNode buildAnd(AndExpr and) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode must = root.putObject("bool").putArray("must");
        must.add(build(and.left()));
        must.add(build(and.right()));
        return root;
    }

    private ObjectNode buildOr(OrExpr or) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode bool = root.putObject("bool");
        ArrayNode should = bool.putArray("should");
        should.add(build(or.left()));
        should.add(build(or.right()));
        bool.put("minimum_should_match", 1);
        return root;
    }

    private ObjectNode buildNot(NotExpr not) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode mustNot = root.putObject("bool").putArray("must_not");
        mustNot.add(build(not.expr()));
        return root;
    }

    private ObjectNode buildMatchAll() {
        ObjectNode root = mapper.createObjectNode();
        root.putObject("match_all");
        return root;
    }
}
