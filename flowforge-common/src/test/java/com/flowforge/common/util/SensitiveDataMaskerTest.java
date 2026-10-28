package com.flowforge.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    @Test
    @DisplayName("URL 查询串中的敏感参数被替换，普通参数保持原样")
    void masksSensitiveQueryParams() {
        String url = "https://api.example.com/data?password=secret123&page=2&access_token=abc.def";

        String masked = SensitiveDataMasker.maskUrl(url);

        assertThat(masked)
                .doesNotContain("secret123")
                .doesNotContain("abc.def")
                .contains("password=***")
                .contains("access_token=***")
                .contains("page=2");
    }

    @Test
    @DisplayName("JSON 中的敏感字段被打码，且输出仍是合法结构")
    void masksJsonFields() {
        String json = "{\"username\":\"demo\",\"token\":\"eyJhbGciOi\",\"nested\":{\"api_key\":\"k-123\"}}";

        String masked = SensitiveDataMasker.maskText(json);

        assertThat(masked)
                .doesNotContain("eyJhbGciOi")
                .doesNotContain("k-123")
                .contains("\"username\":\"demo\"")
                .contains("\"token\":\"***\"");
    }

    @Test
    @DisplayName("Authorization 头整体打码")
    void masksAuthorizationHeader() {
        assertThat(SensitiveDataMasker.maskHeader("Authorization", "Bearer abc.def"))
                .isEqualTo("***");
    }

    @Test
    @DisplayName("普通请求头不做改动")
    void keepsNormalHeader() {
        assertThat(SensitiveDataMasker.maskHeader("Content-Type", "application/json"))
                .isEqualTo("application/json");
    }

    @Test
    @DisplayName("先脱敏再截断，避免截断把敏感值切成仍可识别的前缀")
    void masksBeforeTruncating() {
        String text = "{\"secret\":\"super-long-secret-value-that-would-be-cut\"}";

        String result = SensitiveDataMasker.maskAndTruncate(text, 20);

        assertThat(result).doesNotContain("super-long-secret");
        assertThat(result).startsWith("{\"secret\":\"***\"");
    }

    @Test
    @DisplayName("空值原样返回，不抛异常")
    void handlesNullAndEmpty() {
        assertThat(SensitiveDataMasker.maskUrl(null)).isNull();
        assertThat(SensitiveDataMasker.maskText("")).isEmpty();
        assertThat(SensitiveDataMasker.truncate(null, 10)).isNull();
    }
}
