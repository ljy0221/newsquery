package com.newsquery.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * NQL 캐시 키 생성기
 * NQL 쿼리를 정규화한 후 해시로 변환
 */
public class NQLCacheKeyGenerator {

    public static String generateKey(String nql, int page) {
        return generateKey(nql) + ":page:" + page;
    }

    public static String generateKey(String nql) {
        String normalized = normalize(nql);
        return "nql:" + hash(normalized);
    }

    /**
     * 벡터 임베딩 캐시 키 생성
     */
    public static String generateEmbeddingKey(String text) {
        return "embedding:" + hash(text);
    }

    /**
     * GROUP BY 캐시 키 생성
     */
    public static String generateGroupByKey(String nql, String groupByField) {
        return "groupby:" + hash(nql) + ":" + groupByField;
    }

    /**
     * NQL 정규화
     * - 공백 제거
     * - 소문자 통일
     * - 주석 제거
     */
    private static String normalize(String nql) {
        if (nql == null) return "";

        // 공백 정규화
        return nql
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    /**
     * SHA-256 해시 생성
     */
    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
