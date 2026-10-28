package com.flowforge.scheduler.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 把当前请求的 Authorization 透传给下游服务。
 *
 * <p>这样「内部接口」仍有真实的用户身份，而不是「内网即可信」——
 * 下游服务会独立校验签名，伪造的调用在对方那里直接 401。
 */
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor authorizationRelayInterceptor() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null) {
                    template.header(HttpHeaders.AUTHORIZATION, authorization);
                }
            }
        };
    }
}
