package com.newsquery.query;

import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Elasticsearch function_score 쿼리를 사용한 부스팅 함수
 *
 * 지원하는 부스팅:
 * 1. RECENCY: 최신 문서에 높은 가중치
 * 2. TREND: 트렌드 점수 기반 부스팅
 */
public class BoostingFunction {

    private static final ObjectMapper mapper = new ObjectMapper();

    public enum BoostType {
        RECENCY("recency"),
        TREND("trend"),
        POPULARITY("popularity");

        private final String name;

        BoostType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * RECENCY 부스팅: 최신 뉴스에 높은 점수
     *
     * 공식: score * (1 + (days_since_publish / decay_distance))
     *
     * 예: 7일 전 문서는 원래 점수의 2배, 14일 전은 1.5배
     */
    public static ObjectNode buildRecencyBoost(String dateField, int decayDays) {
        ObjectNode root = mapper.createObjectNode();

        ObjectNode decay = root.putObject("gauss");
        ObjectNode dateDecay = decay.putObject(dateField);

        // "지금으로부터" 기준
        dateDecay.put("origin", "now");
        dateDecay.put("scale", decayDays + "d");
        dateDecay.put("decay", 0.5); // 멀어질수록 0.5배씩 감소

        return root;
    }

    /**
     * TREND 부스팅: 트렌드 점수 기반
     *
     * 필드: trend_score (0~100)
     * 공식: score * (1 + trend_score / 100)
     */
    public static ObjectNode buildTrendBoost(String trendField) {
        ObjectNode root = mapper.createObjectNode();

        ObjectNode fieldValueFactor = root.putObject("field_value_factor");
        fieldValueFactor.put("field", trendField);
        fieldValueFactor.put("factor", 0.01); // trend_score를 0~1로 정규화
        fieldValueFactor.put("modifier", "sqrt"); // 제곱근으로 완화
        fieldValueFactor.put("missing", 1); // 값 없으면 1 (가중치 없음)

        return root;
    }

    /**
     * POPULARITY 부스팅: 조회수/공유수 기반
     *
     * 필드: popularity_score (조회수/공유수 합산)
     */
    public static ObjectNode buildPopularityBoost(String popularityField) {
        ObjectNode root = mapper.createObjectNode();

        ObjectNode fieldValueFactor = root.putObject("field_value_factor");
        fieldValueFactor.put("field", popularityField);
        fieldValueFactor.put("factor", 0.001); // 스케일 조정
        fieldValueFactor.put("modifier", "log1p"); // log(1 + value)로 큰 수 완화
        fieldValueFactor.put("missing", 1);

        return root;
    }

    /**
     * 복합 부스팅: 여러 함수 조합
     *
     * 예: recency + trend 부스팅
     */
    public static ObjectNode buildCompositeBoost(
        ObjectNode baseQuery,
        ObjectNode... boostFunctions
    ) {
        ObjectNode root = mapper.createObjectNode();

        // 기본 쿼리 설정
        root.set("query", baseQuery);

        // 부스팅 함수 배열
        ArrayNode functionsArray = root.putArray("functions");
        for (ObjectNode boostFunc : boostFunctions) {
            functionsArray.add(boostFunc);
        }

        // 점수 모드 (multiply = 원래 점수 * 부스팅값)
        root.put("score_mode", "multiply");

        // 부스팅 모드 (multiply = 최종 점수에 곱하기)
        root.put("boost_mode", "multiply");

        // 최소 점수 (음수 점수 제거)
        root.put("min_score", 0.1);

        return root;
    }

    /**
     * Function Score 쿼리 생성
     *
     * Elasticsearch에 보낼 최종 쿼리 구조:
     * {
     *   "query": {
     *     "function_score": {
     *       "query": { ... },
     *       "functions": [
     *         { "gauss": { "publishedAt": {...} } },
     *         { "field_value_factor": { "field": "trend_score", ... } }
     *       ],
     *       "score_mode": "multiply",
     *       "boost_mode": "multiply"
     *     }
     *   }
     * }
     */
    public static ObjectNode buildFunctionScoreQuery(
        ObjectNode boolQuery,
        ObjectNode... boostFunctions
    ) {
        ObjectNode root = mapper.createObjectNode();

        ObjectNode functionScore = root.putObject("function_score");
        functionScore.set("query", boolQuery);

        ArrayNode functionsArray = functionScore.putArray("functions");
        for (ObjectNode boostFunc : boostFunctions) {
            functionsArray.add(boostFunc);
        }

        functionScore.put("score_mode", "multiply");
        functionScore.put("boost_mode", "multiply");
        functionScore.put("max_boost", 42.0);

        return root;
    }
}
