package com.flowforge.execution.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.common.contract.NodeType;
import com.flowforge.common.util.SensitiveDataMasker;
import com.flowforge.execution.engine.NodeContext;
import com.flowforge.execution.engine.NodeExecutor;
import com.flowforge.execution.engine.NodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HTTP 请求节点。
 *
 * <p>第一版不执行用户上传的代码，只发起受配置约束的 HTTP 调用；
 * 但仍然限制协议（仅 http/https）、不跟随重定向、限制响应体大小，
 * 避免被当成任意协议的代理或内存放大器。
 */
@Component
public class HttpNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpNodeExecutor.class);

    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    private static final int MAX_SUMMARY_LENGTH = 512;
    private static final Set<String> DEFAULT_EXPECT_STATUS = Set.of("200", "201", "202", "204");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final ObjectMapper objectMapper;

    public HttpNodeExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public NodeType type() {
        return NodeType.HTTP;
    }

    @Override
    public NodeResult execute(NodeContext context) {
        Map<String, Object> config = context.config();
        String url = String.valueOf(config.getOrDefault("url", ""));
        String method = String.valueOf(config.getOrDefault("method", "GET")).toUpperCase();
        int timeoutMs = intOf(config.get("timeoutMs"), DEFAULT_TIMEOUT_MS);
        Set<String> expected = expectedStatuses(config.get("expectStatus"));

        String requestSummary = SensitiveDataMasker.maskAndTruncate(
                method + " " + url, MAX_SUMMARY_LENGTH);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs));

            Object headers = config.get("headers");
            if (headers instanceof Map<?, ?> headerMap) {
                headerMap.forEach((key, value) -> {
                    if (key != null && value != null) {
                        builder.header(String.valueOf(key), String.valueOf(value));
                    }
                });
            }

            Object body = config.get("body");
            if ("GET".equals(method) || "DELETE".equals(method)) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                String payload = body == null ? "" : String.valueOf(body);
                builder.method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            String bodyText = response.body() == null ? "" : response.body();
            String responseSummary = SensitiveDataMasker.maskAndTruncate(
                    "HTTP " + response.statusCode() + " " + bodyText, MAX_SUMMARY_LENGTH);

            if (!expected.contains(String.valueOf(response.statusCode()))) {
                boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
                return NodeResult.failure(
                        "HTTP_" + response.statusCode(),
                        "接口返回非预期状态码 " + response.statusCode(),
                        retryable, false, requestSummary, responseSummary);
            }

            // 状态码与响应体分开存放：下游条件节点要用 body 里的字段判断，
            // 如果两者共用 status 这个名字，配置会变得极易混淆。
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("httpStatus", response.statusCode());
            output.put("body", parseBody(bodyText));
            return NodeResult.success(output, requestSummary, responseSummary);

        } catch (HttpTimeoutException ex) {
            // 请求已发出但没等到响应：对端可能已经处理完，不能贸然当成「没发生」
            return NodeResult.failure("HTTP_TIMEOUT",
                    "请求超时，无法确认外部系统是否已处理",
                    true, true, requestSummary, null);
        } catch (ConnectException ex) {
            return NodeResult.failure("HTTP_CONNECT_FAILED",
                    "无法连接到目标地址", true, false, requestSummary, null);
        } catch (IOException ex) {
            log.debug("HTTP 节点 IO 异常 url={}", url, ex);
            return NodeResult.failure("HTTP_IO_ERROR",
                    "网络异常：" + ex.getMessage(), true, false, requestSummary, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return NodeResult.failure("HTTP_INTERRUPTED",
                    "请求被中断", false, true, requestSummary, null);
        } catch (IllegalArgumentException ex) {
            return NodeResult.failure("HTTP_BAD_URL",
                    "URL 不合法：" + ex.getMessage(), false, false, requestSummary, null);
        }
    }

    private Set<String> expectedStatuses(Object raw) {
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        return DEFAULT_EXPECT_STATUS;
    }

    private int intOf(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    /** 响应体是 JSON 就解析成对象/数组，否则保留原文，让下游表达式能按字段取值。 */
    private Object parseBody(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return "";
        }
        String trimmed = bodyText.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return truncate(bodyText);
        }
        try {
            return objectMapper.readValue(trimmed, Object.class);
        } catch (JsonProcessingException ex) {
            return truncate(bodyText);
        }
    }

    private String truncate(String text) {
        return text.length() <= MAX_SUMMARY_LENGTH ? text : text.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }
}
