# NQL Parser Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** ANTLR4로 NQL(News Query Language)을 파싱하고 Elasticsearch 8.x Query DSL로 변환하는 Spring Boot 백엔드를 구현한다.

**Architecture:** NQL 문자열 → ANTLR4 파서(NQL.g4) → NQLExpression IR → ESQueryBuilder → ES bool/retriever query JSON. RRF는 BM25 query + kNN query를 ES retriever API로 묶어 하이브리드 랭킹을 제공한다.

**Tech Stack:** Java 17, Spring Boot 3.2, ANTLR4 4.13, Elasticsearch Java Client 8.x, Jackson, JUnit 5

---

## Task 1: Spring Boot 프로젝트 초기화

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`
- Create: `src/main/java/com/newsquery/NewsQueryApplication.java`
- Create: `src/main/resources/application.yml`

### Step 1: settings.gradle 작성

```groovy
rootProject.name = 'newsquery'
```

### Step 2: build.gradle 작성

```groovy
plugins {
    id 'java'
    id 'antlr'
    id 'org.springframework.boot' version '3.2.3'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.newsquery'
version = '0.0.1-SNAPSHOT'

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    // ANTLR4
    antlr 'org.antlr:antlr4:4.13.1'

    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // Elasticsearch Java Client
    implementation 'co.elastic.clients:elasticsearch-java:8.12.0'
    implementation 'com.fasterxml.jackson.core:jackson-databind'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

generateGrammarSource {
    arguments += ['-visitor', '-package', 'com.newsquery.nql']
    outputDirectory = new File("${buildDir}/generated-src/antlr/main/com/newsquery/nql")
}

compileJava {
    dependsOn generateGrammarSource
}

sourceSets {
    main {
        java {
            srcDir "${buildDir}/generated-src/antlr/main"
        }
    }
}
```

### Step 3: 메인 애플리케이션 클래스 작성

`src/main/java/com/newsquery/NewsQueryApplication.java`:
```java
package com.newsquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NewsQueryApplication {
    public static void main(String[] args) {
        SpringApplication.run(NewsQueryApplication.class, args);
    }
}
```

### Step 4: application.yml 작성

`src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: newsquery

elasticsearch:
  host: localhost
  port: 9200
  index: news
```

### Step 5: 빌드 확인

```bash
./gradlew build -x test
```
Expected: `BUILD SUCCESSFUL`

### Step 6: 커밋

```bash
git init
git add build.gradle settings.gradle src/main/java/com/newsquery/NewsQueryApplication.java src/main/resources/application.yml
git commit -m "feat: Spring Boot 프로젝트 초기화"
```

---

## Task 2: NQL.g4 Grammar 파일 작성

**Files:**
- Create: `src/main/antlr4/com/newsquery/NQL.g4`

### Step 1: Grammar 파일 작성

`src/main/antlr4/com/newsquery/NQL.g4`:
```antlr
grammar NQL;

// === Parser Rules ===

query       : expr EOF ;

expr        : expr AND expr          # andExpr
            | expr OR expr           # orExpr
            | '!' expr               # notExpr
            | '(' expr ')'           # groupExpr
            | keywordExpr            # kwExpr
            | fieldExpr              # fieldExprRule
            ;

keywordExpr : KEYWORD '(' STRING ')' ('*' NUMBER)? ;

fieldExpr   : field compOp value
            | field IN '[' valueList ']'
            ;

field       : SENTIMENT | SOURCE | CATEGORY | COUNTRY | PUBLISHED_AT | SCORE ;

compOp      : EQ | NEQ | GTE | LTE | GT | LT ;

value       : STRING | NUMBER ;

valueList   : value (',' value)* ;

// === Lexer Rules ===

KEYWORD      : 'keyword' ;
AND          : 'AND' ;
OR           : 'OR' ;
IN           : 'IN' ;

SENTIMENT    : 'sentiment' ;
SOURCE       : 'source' ;
CATEGORY     : 'category' ;
COUNTRY      : 'country' ;
PUBLISHED_AT : 'publishedAt' ;
SCORE        : 'score' ;

EQ           : '==' ;
NEQ          : '!=' ;
GTE          : '>=' ;
LTE          : '<=' ;
GT           : '>' ;
LT           : '<' ;

NUMBER       : [0-9]+ ('.' [0-9]+)? ;
STRING       : '"' (~["\r\n])* '"' ;
WS           : [ \t\r\n]+ -> skip ;
```

### Step 2: 파서 코드 생성

```bash
./gradlew generateGrammarSource
```
Expected: `build/generated-src/antlr/main/com/newsquery/nql/` 디렉토리에 `NQLLexer.java`, `NQLParser.java`, `NQLVisitor.java`, `NQLBaseVisitor.java` 생성 확인

### Step 3: 커밋

```bash
git add src/main/antlr4/com/newsquery/NQL.g4
git commit -m "feat: NQL ANTLR4 Grammar 정의"
```

---

## Task 3: NQLExpression 중간 표현(IR) 모델

**Files:**
- Create: `src/main/java/com/newsquery/nql/NQLExpression.java`
- Create: `src/test/java/com/newsquery/nql/NQLExpressionTest.java`

### Step 1: 실패 테스트 작성

`src/test/java/com/newsquery/nql/NQLExpressionTest.java`:
```java
package com.newsquery.nql;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class NQLExpressionTest {

    @Test
    void keywordExpr_withBoost() {
        var expr = new NQLExpression.KeywordExpr("HBM", 1.5);
        assertThat(expr.text()).isEqualTo("HBM");
        assertThat(expr.boost()).isEqualTo(1.5);
    }

    @Test
    void keywordExpr_withoutBoost() {
        var expr = new NQLExpression.KeywordExpr("HBM", null);
        assertThat(expr.boost()).isNull();
    }

    @Test
    void compareExpr() {
        var expr = new NQLExpression.CompareExpr("sentiment", "==", "positive");
        assertThat(expr.field()).isEqualTo("sentiment");
        assertThat(expr.op()).isEqualTo("==");
        assertThat(expr.value()).isEqualTo("positive");
    }

    @Test
    void inExpr() {
        var expr = new NQLExpression.InExpr("source", List.of("Reuters", "Bloomberg"));
        assertThat(expr.field()).isEqualTo("source");
        assertThat(expr.values()).containsExactly("Reuters", "Bloomberg");
    }

    @Test
    void andExpr() {
        var left = new NQLExpression.KeywordExpr("HBM", null);
        var right = new NQLExpression.CompareExpr("sentiment", "!=", "negative");
        var and = new NQLExpression.AndExpr(left, right);
        assertThat(and.left()).isEqualTo(left);
        assertThat(and.right()).isEqualTo(right);
    }
}
```

### Step 2: 테스트 실패 확인

```bash
./gradlew test --tests "com.newsquery.nql.NQLExpressionTest"
```
Expected: FAIL (클래스 없음)

### Step 3: NQLExpression 구현

`src/main/java/com/newsquery/nql/NQLExpression.java`:
```java
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
```

### Step 4: 테스트 통과 확인

```bash
./gradlew test --tests "com.newsquery.nql.NQLExpressionTest"
```
Expected: PASS

### Step 5: 커밋

```bash
git add src/main/java/com/newsquery/nql/NQLExpression.java \
        src/test/java/com/newsquery/nql/NQLExpressionTest.java
git commit -m "feat: NQLExpression IR 모델 정의"
```

---

## Task 4: NQLVisitorImpl — AST → NQLExpression 변환

**Files:**
- Create: `src/main/java/com/newsquery/nql/NQLVisitorImpl.java`
- Create: `src/test/java/com/newsquery/nql/NQLVisitorTest.java`

> **선행 조건:** Task 2 완료 후 `./gradlew generateGrammarSource` 실행 필요 (NQLBaseVisitor 클래스 존재해야 함)

### Step 1: 실패 테스트 작성

`src/test/java/com/newsquery/nql/NQLVisitorTest.java`:
```java
package com.newsquery.nql;

import org.antlr.v4.runtime.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class NQLVisitorTest {

    private NQLExpression parse(String nql) {
        CharStream input = CharStreams.fromString(nql);
        NQLLexer lexer = new NQLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        NQLParser parser = new NQLParser(tokens);
        NQLVisitorImpl visitor = new NQLVisitorImpl();
        return visitor.visit(parser.query());
    }

    @Test
    void keywordOnly() {
        var result = parse("keyword(\"HBM\")");
        assertThat(result).isEqualTo(new NQLExpression.KeywordExpr("HBM", null));
    }

    @Test
    void keywordWithBoost() {
        var result = parse("keyword(\"HBM\") * 1.5");
        assertThat(result).isEqualTo(new NQLExpression.KeywordExpr("HBM", 1.5));
    }

    @Test
    void sentimentEquals() {
        var result = parse("sentiment == \"positive\"");
        assertThat(result).isEqualTo(new NQLExpression.CompareExpr("sentiment", "==", "positive"));
    }

    @Test
    void sourceIn() {
        var result = parse("source IN [\"Reuters\", \"Bloomberg\"]");
        assertThat(result).isEqualTo(new NQLExpression.InExpr("source", List.of("Reuters", "Bloomberg")));
    }

    @Test
    void andExpression() {
        var result = parse("keyword(\"HBM\") AND sentiment == \"positive\"");
        var expected = new NQLExpression.AndExpr(
            new NQLExpression.KeywordExpr("HBM", null),
            new NQLExpression.CompareExpr("sentiment", "==", "positive")
        );
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void notExpression() {
        var result = parse("!sentiment == \"negative\"");
        var expected = new NQLExpression.NotExpr(
            new NQLExpression.CompareExpr("sentiment", "==", "negative")
        );
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void complexQuery() {
        var result = parse("keyword(\"HBM\") * 2.0 AND sentiment != \"negative\"");
        assertThat(result).isInstanceOf(NQLExpression.AndExpr.class);
        var and = (NQLExpression.AndExpr) result;
        assertThat(and.left()).isEqualTo(new NQLExpression.KeywordExpr("HBM", 2.0));
        assertThat(and.right()).isEqualTo(new NQLExpression.CompareExpr("sentiment", "!=", "negative"));
    }
}
```

### Step 2: 테스트 실패 확인

```bash
./gradlew test --tests "com.newsquery.nql.NQLVisitorTest"
```
Expected: FAIL (NQLVisitorImpl 없음)

### Step 3: NQLVisitorImpl 구현

`src/main/java/com/newsquery/nql/NQLVisitorImpl.java`:
```java
package com.newsquery.nql;

import org.antlr.v4.runtime.tree.ParseTree;
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
```

### Step 4: 테스트 통과 확인

```bash
./gradlew test --tests "com.newsquery.nql.NQLVisitorTest"
```
Expected: PASS (7 tests)

### Step 5: 커밋

```bash
git add src/main/java/com/newsquery/nql/NQLVisitorImpl.java \
        src/test/java/com/newsquery/nql/NQLVisitorTest.java
git commit -m "feat: NQLVisitorImpl — AST to NQLExpression 변환"
```

---

## Task 5: ESQueryBuilder — NQLExpression → ES Query DSL

**Files:**
- Create: `src/main/java/com/newsquery/query/ESQueryBuilder.java`
- Create: `src/test/java/com/newsquery/query/ESQueryBuilderTest.java`

### Step 1: 실패 테스트 작성

`src/test/java/com/newsquery/query/ESQueryBuilderTest.java`:
```java
package com.newsquery.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsquery.nql.NQLExpression;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ESQueryBuilderTest {

    private final ESQueryBuilder builder = new ESQueryBuilder();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keywordWithoutBoost() throws Exception {
        var expr = new NQLExpression.KeywordExpr("HBM", null);
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("match").path("content").asText()).isEqualTo("HBM");
    }

    @Test
    void keywordWithBoost() throws Exception {
        var expr = new NQLExpression.KeywordExpr("HBM", 1.5);
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("match").path("content").path("query").asText()).isEqualTo("HBM");
        assertThat(result.path("match").path("content").path("boost").asDouble()).isEqualTo(1.5);
    }

    @Test
    void sentimentEquals() throws Exception {
        var expr = new NQLExpression.CompareExpr("sentiment", "==", "positive");
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("term").path("sentiment").asText()).isEqualTo("positive");
    }

    @Test
    void sentimentNotEquals() throws Exception {
        var expr = new NQLExpression.CompareExpr("sentiment", "!=", "negative");
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("bool").path("must_not").get(0)
            .path("term").path("sentiment").asText()).isEqualTo("negative");
    }

    @Test
    void publishedAtRange() throws Exception {
        var expr = new NQLExpression.CompareExpr("publishedAt", ">=", "2024-01-01");
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("range").path("publishedAt").path("gte").asText())
            .isEqualTo("2024-01-01");
    }

    @Test
    void sourceIn() throws Exception {
        var expr = new NQLExpression.InExpr("source", List.of("Reuters", "Bloomberg"));
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        JsonNode values = result.path("terms").path("source");
        assertThat(values.get(0).asText()).isEqualTo("Reuters");
        assertThat(values.get(1).asText()).isEqualTo("Bloomberg");
    }

    @Test
    void andExpression() throws Exception {
        var expr = new NQLExpression.AndExpr(
            new NQLExpression.KeywordExpr("HBM", 2.0),
            new NQLExpression.CompareExpr("sentiment", "!=", "negative")
        );
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        JsonNode must = result.path("bool").path("must");
        assertThat(must.isArray()).isTrue();
        assertThat(must.size()).isEqualTo(2);
    }

    @Test
    void orExpression() throws Exception {
        var expr = new NQLExpression.OrExpr(
            new NQLExpression.KeywordExpr("HBM", null),
            new NQLExpression.KeywordExpr("GPU", null)
        );
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        JsonNode should = result.path("bool").path("should");
        assertThat(should.isArray()).isTrue();
        assertThat(result.path("bool").path("minimum_should_match").asInt()).isEqualTo(1);
    }
}
```

### Step 2: 테스트 실패 확인

```bash
./gradlew test --tests "com.newsquery.query.ESQueryBuilderTest"
```
Expected: FAIL

### Step 3: ESQueryBuilder 구현

`src/main/java/com/newsquery/query/ESQueryBuilder.java`:
```java
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
        return switch (expr) {
            case AndExpr and -> buildAnd(and);
            case OrExpr or -> buildOr(or);
            case NotExpr not -> buildNot(not);
            case KeywordExpr kw -> buildKeyword(kw);
            case CompareExpr cmp -> buildCompare(cmp);
            case InExpr in -> buildIn(in);
        };
    }

    private ObjectNode buildKeyword(KeywordExpr kw) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode match = root.putObject("match");
        if (kw.boost() != null) {
            ObjectNode content = match.putObject("content");
            content.put("query", kw.text());
            content.put("boost", kw.boost());
        } else {
            match.put("content", kw.text());
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
        // Range operators: >, >=, <, <=
        String rangeOp = RANGE_OPS.get(cmp.op());
        ObjectNode root = mapper.createObjectNode();
        root.putObject("range").putObject(cmp.field()).put(rangeOp, cmp.value());
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
}
```

### Step 4: 테스트 통과 확인

```bash
./gradlew test --tests "com.newsquery.query.ESQueryBuilderTest"
```
Expected: PASS (8 tests)

### Step 5: 커밋

```bash
git add src/main/java/com/newsquery/query/ESQueryBuilder.java \
        src/test/java/com/newsquery/query/ESQueryBuilderTest.java
git commit -m "feat: ESQueryBuilder — NQLExpression to ES Query DSL 변환"
```

---

## Task 6: NQLParser 파사드 — 파싱 진입점

**Files:**
- Create: `src/main/java/com/newsquery/nql/NQLParser.java` ⚠️ 이름 충돌 주의: ANTLR4 생성 클래스와 구분을 위해 `NQLQueryParser.java`로 명명
- Create: `src/test/java/com/newsquery/nql/NQLQueryParserTest.java`

### Step 1: 실패 테스트 작성

`src/test/java/com/newsquery/nql/NQLQueryParserTest.java`:
```java
package com.newsquery.nql;

import com.newsquery.query.ESQueryBuilder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NQLQueryParserTest {

    private final NQLQueryParser parser = new NQLQueryParser();

    @Test
    void parseAndBuildQuery() {
        var result = parser.parseToQuery("keyword(\"HBM\") * 2.0 AND sentiment != \"negative\"");
        assertThat(result.toString()).contains("HBM");
        assertThat(result.toString()).contains("negative");
    }

    @Test
    void invalidNql_throwsException() {
        assertThatThrownBy(() -> parser.parseToQuery("INVALID @@@ query"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NQL 파싱 오류");
    }
}
```

### Step 2: 테스트 실패 확인

```bash
./gradlew test --tests "com.newsquery.nql.NQLQueryParserTest"
```
Expected: FAIL

### Step 3: NQLQueryParser 구현

`src/main/java/com/newsquery/nql/NQLQueryParser.java`:
```java
package com.newsquery.nql;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.newsquery.query.ESQueryBuilder;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.springframework.stereotype.Component;

@Component
public class NQLQueryParser {

    private final ESQueryBuilder queryBuilder = new ESQueryBuilder();

    public ObjectNode parseToQuery(String nql) {
        CharStream input = CharStreams.fromString(nql);

        NQLLexer lexer = new NQLLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(ThrowingErrorListener.INSTANCE);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        NQLParser parser = new NQLParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(ThrowingErrorListener.INSTANCE);

        try {
            NQLExpression expr = new NQLVisitorImpl().visit(parser.query());
            return queryBuilder.build(expr);
        } catch (ParseCancellationException e) {
            throw new IllegalArgumentException("NQL 파싱 오류: " + e.getMessage(), e);
        }
    }

    private static class ThrowingErrorListener extends BaseErrorListener {
        static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine,
                                String msg, RecognitionException e) {
            throw new ParseCancellationException("line " + line + ":" + charPositionInLine + " " + msg);
        }
    }
}
```

### Step 4: 테스트 통과 확인

```bash
./gradlew test --tests "com.newsquery.nql.NQLQueryParserTest"
```
Expected: PASS

### Step 5: 커밋

```bash
git add src/main/java/com/newsquery/nql/NQLQueryParser.java \
        src/test/java/com/newsquery/nql/NQLQueryParserTest.java
git commit -m "feat: NQLQueryParser 파사드 — 파싱 + 에러 처리 통합"
```

---

## Task 7: RRFScorer — BM25 + kNN 하이브리드 쿼리 빌더

**Files:**
- Create: `src/main/java/com/newsquery/scoring/RRFScorer.java`
- Create: `src/test/java/com/newsquery/scoring/RRFScorerTest.java`

### Step 1: 실패 테스트 작성

`src/test/java/com/newsquery/scoring/RRFScorerTest.java`:
```java
package com.newsquery.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.newsquery.nql.NQLExpression;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RRFScorerTest {

    private final RRFScorer scorer = new RRFScorer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void withKeyword_includesKnnRetriever() throws Exception {
        ObjectNode boolQuery = (ObjectNode) mapper.readTree("{\"bool\":{\"must\":[]}}");
        var keywords = java.util.List.of(new NQLExpression.KeywordExpr("HBM", null));
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};

        JsonNode result = scorer.buildRetriever(boolQuery, keywords, vector);

        assertThat(result.path("rrf").path("retrievers").isArray()).isTrue();
        assertThat(result.path("rrf").path("retrievers").size()).isEqualTo(2);
        assertThat(result.path("rrf").path("rank_constant").asInt()).isEqualTo(60);
    }

    @Test
    void withoutKeyword_onlyBm25Retriever() throws Exception {
        ObjectNode boolQuery = (ObjectNode) mapper.readTree("{\"bool\":{\"must\":[]}}");
        var keywords = java.util.List.<NQLExpression.KeywordExpr>of();

        JsonNode result = scorer.buildRetriever(boolQuery, keywords, null);

        assertThat(result.path("standard").path("query")).isNotNull();
        assertThat(result.has("rrf")).isFalse();
    }
}
```

### Step 2: 테스트 실패 확인

```bash
./gradlew test --tests "com.newsquery.scoring.RRFScorerTest"
```
Expected: FAIL

### Step 3: RRFScorer 구현

`src/main/java/com/newsquery/scoring/RRFScorer.java`:
```java
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
        retrievers.add(buildStandard(boolQuery));
        retrievers.add(buildKnn(vector));
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
        knn.put("num_candidates", KNN_CANDIDATES);
        return knn;
    }
}
```

### Step 4: 테스트 통과 확인

```bash
./gradlew test --tests "com.newsquery.scoring.RRFScorerTest"
```
Expected: PASS

### Step 5: 커밋

```bash
git add src/main/java/com/newsquery/scoring/RRFScorer.java \
        src/test/java/com/newsquery/scoring/RRFScorerTest.java
git commit -m "feat: RRFScorer — BM25 + kNN retriever 빌더"
```

---

## Task 8: QueryController — REST API

**Files:**
- Create: `src/main/java/com/newsquery/api/QueryController.java`
- Create: `src/main/java/com/newsquery/api/QueryRequest.java`
- Create: `src/test/java/com/newsquery/api/QueryControllerTest.java`

### Step 1: 실패 테스트 작성

`src/test/java/com/newsquery/api/QueryControllerTest.java`:
```java
package com.newsquery.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.newsquery.nql.NQLQueryParser;
import com.newsquery.scoring.RRFScorer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean NQLQueryParser nqlQueryParser;
    @MockBean RRFScorer rrfScorer;

    @Test
    void postQuery_withValidNql_returns200() throws Exception {
        var request = new QueryRequest("keyword(\"HBM\") AND sentiment == \"positive\"");

        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    void postQuery_withEmptyNql_returns400() throws Exception {
        var request = new QueryRequest("");

        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
```

### Step 2: 테스트 실패 확인

```bash
./gradlew test --tests "com.newsquery.api.QueryControllerTest"
```
Expected: FAIL

### Step 3: QueryRequest, QueryController 구현

`src/main/java/com/newsquery/api/QueryRequest.java`:
```java
package com.newsquery.api;

public record QueryRequest(String nql) {}
```

`src/main/java/com/newsquery/api/QueryController.java`:
```java
package com.newsquery.api;

import com.newsquery.nql.NQLQueryParser;
import com.newsquery.scoring.RRFScorer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final NQLQueryParser nqlQueryParser;
    private final RRFScorer rrfScorer;

    public QueryController(NQLQueryParser nqlQueryParser, RRFScorer rrfScorer) {
        this.nqlQueryParser = nqlQueryParser;
        this.rrfScorer = rrfScorer;
    }

    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody QueryRequest request) {
        if (request.nql() == null || request.nql().isBlank()) {
            return ResponseEntity.badRequest().body("nql 필드가 비어있습니다.");
        }
        try {
            var esQuery = nqlQueryParser.parseToQuery(request.nql());
            // TODO: ES 실행 연동 (현재는 생성된 쿼리 반환)
            return ResponseEntity.ok(esQuery);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
```

### Step 4: 테스트 통과 확인

```bash
./gradlew test --tests "com.newsquery.api.QueryControllerTest"
```
Expected: PASS

### Step 5: 전체 테스트 실행

```bash
./gradlew test
```
Expected: 전체 PASS

### Step 6: 최종 커밋

```bash
git add src/main/java/com/newsquery/api/ \
        src/test/java/com/newsquery/api/
git commit -m "feat: QueryController — POST /api/query 엔드포인트"
```

---

## 검증 요약

```bash
# 전체 빌드
./gradlew build

# 레이어별 테스트
./gradlew test --tests "com.newsquery.nql.NQLExpressionTest"
./gradlew test --tests "com.newsquery.nql.NQLVisitorTest"
./gradlew test --tests "com.newsquery.query.ESQueryBuilderTest"
./gradlew test --tests "com.newsquery.nql.NQLQueryParserTest"
./gradlew test --tests "com.newsquery.scoring.RRFScorerTest"
./gradlew test --tests "com.newsquery.api.QueryControllerTest"

# 서버 기동 확인
./gradlew bootRun

# API 호출 테스트
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"nql": "keyword(\"HBM\") * 2.0 AND sentiment != \"negative\""}'
```
