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
