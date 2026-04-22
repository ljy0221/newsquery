package com.newsquery.query;

import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsquery.nql.NQLExpression;
import org.springframework.stereotype.Component;

@Component
public class AggregationBuilder {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * GROUP BY 절을 Elasticsearch 집계로 변환
     *
     * 예: keyword("AI") GROUP BY category LIMIT 10
     *
     * → {
     *      "aggs": {
     *        "group_by_category": {
     *          "terms": { "field": "category", "size": 10 }
     *        }
     *      }
     *    }
     */
    public ObjectNode buildAggregation(NQLExpression.AggregationExpr agg) {
        ObjectNode root = mapper.createObjectNode();

        ObjectNode aggsNode = root.putObject("aggs");
        ObjectNode groupAgg = aggsNode.putObject("group_by_" + agg.groupByField());

        ObjectNode termsAgg = groupAgg.putObject("terms");
        termsAgg.put("field", agg.groupByField());
        termsAgg.put("size", agg.limit().orElse(10));

        return root;
    }

    /**
     * 쿼리 + 집계를 함께 빌드
     *
     * Elasticsearch에 보낼 최종 쿼리:
     * {
     *   "query": { ... },
     *   "aggs": { ... }
     * }
     */
    public ObjectNode buildQueryWithAggregation(
        NQLExpression.AggregationExpr agg,
        ObjectNode boolQuery
    ) {
        ObjectNode root = mapper.createObjectNode();
        root.set("query", boolQuery);

        ObjectNode aggsNode = root.putObject("aggs");
        ObjectNode groupAgg = aggsNode.putObject("group_by_" + agg.groupByField());

        ObjectNode termsAgg = groupAgg.putObject("terms");
        termsAgg.put("field", agg.groupByField());
        termsAgg.put("size", agg.limit().orElse(10));

        return root;
    }

    /**
     * JsonNode 쿼리에 집계를 추가 (RRF retriever용)
     */
    public ObjectNode buildQueryWithAggregation(
        Object retriever,
        String groupByField,
        Integer limit
    ) {
        ObjectNode root = mapper.createObjectNode();

        if (retriever instanceof ObjectNode objRetriever) {
            root.set("retriever", objRetriever);
        } else {
            root.set("retriever", mapper.valueToTree(retriever));
        }

        ObjectNode aggsNode = root.putObject("aggs");
        ObjectNode groupAgg = aggsNode.putObject("group_by_" + groupByField);

        ObjectNode termsAgg = groupAgg.putObject("terms");
        termsAgg.put("field", groupByField);
        termsAgg.put("size", limit != null ? limit : 10);

        return root;
    }
}
