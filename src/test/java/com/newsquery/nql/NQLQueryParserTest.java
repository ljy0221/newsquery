package com.newsquery.nql;

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
