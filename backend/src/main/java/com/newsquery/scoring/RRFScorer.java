package com.newsquery.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.newsquery.nql.NQLExpression;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RRFScorer {

    private static final int RANK_CONSTANT = 60;
    private static final int RANK_WINDOW_SIZE = 100;
    private static final int KNN_CANDIDATES = 100;
    private static final String VECTOR_FIELD = "content_vector";

    /**
     * RRF 파라미터 설명:
     * - RANK_CONSTANT (60): RRF 공식 1/(k + rank)의 k값. 크클수록 순위 차이를 완화
     * - RANK_WINDOW_SIZE (100): 각 retriever에서 반환할 최대 문서 수
     * - KNN_CANDIDATES (100): kNN 검색에서 평가할 후보 문서 수
     * - VECTOR_FIELD: content_vector (384 dims, all-MiniLM-L6-v2 모델)
     *
     * 성능 특성:
     * - 기본값은 balanced 설정 (정확도 vs 속도)
     * - 큰 데이터셋: RANK_WINDOW_SIZE를 50으로 줄여서 성능 향상
     * - 정확도 중시: RANK_CONSTANT를 120으로 올려서 순위 가중치 증가
     */

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * keyword 표현식이 있으면 RRF retriever(BM25 + kNN),
     * 없으면 BM25 단독 retriever를 반환한다.
     *
     * @param boolQuery  ESQueryBuilder가 생성한 bool query
     * @param keywords   NQL의 keyword() 표현식 목록
     * @param vector     keyword 텍스트를 임베딩한 벡터 (null 허용 시 kNN 생략)
     */
    public JsonNode buildRetriever(ObjectNode boolQuery,
                                   List<NQLExpression.KeywordExpr> keywords,
                                   float[] vector) {
        if (keywords.isEmpty() || vector == null) {
            // BM25 단독
            ObjectNode root = mapper.createObjectNode();
            root.set("standard", buildStandard(boolQuery));
            return root;
        }

        // RRF: BM25 + kNN
        ObjectNode rrf = mapper.createObjectNode();
        ArrayNode retrievers = rrf.putArray("retrievers");

        ObjectNode standardRetriever = mapper.createObjectNode();
        standardRetriever.set("standard", buildStandard(boolQuery));
        retrievers.add(standardRetriever);

        ObjectNode knnRetriever = mapper.createObjectNode();
        knnRetriever.set("knn", buildKnn(vector));
        retrievers.add(knnRetriever);
        rrf.put("rank_window_size", RANK_WINDOW_SIZE);
        rrf.put("rank_constant", RANK_CONSTANT);

        ObjectNode root = mapper.createObjectNode();
        root.set("rrf", rrf);
        return root;
    }

    private ObjectNode buildStandard(ObjectNode boolQuery) {
        ObjectNode standard = mapper.createObjectNode();
        standard.set("query", boolQuery);
        return standard;
    }

    private ObjectNode buildKnn(float[] vector) {
        ObjectNode knn = mapper.createObjectNode();
        knn.put("field", VECTOR_FIELD);
        ArrayNode queryVector = knn.putArray("query_vector");
        for (float v : vector) queryVector.add(v);
        knn.put("k", RANK_WINDOW_SIZE);
        knn.put("num_candidates", KNN_CANDIDATES);
        return knn;
    }
}