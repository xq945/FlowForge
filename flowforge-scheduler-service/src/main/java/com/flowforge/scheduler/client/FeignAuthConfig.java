package com.flowforge.scheduler.client;

import com.flowforge.common.security.JwtTokenService;
import com.flowforge.common.security.LoginUser;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * Feign 调用统一携带身份。
 *
 * <p>用户请求发起的调用透传用户自己的 Authorization，下游据此做归属校验；
 * 周期任务这类**没有用户上下文**的调用，使用一个仅含 SYSTEM 角色的短期令牌，
 * 下游仍然会独立校验签名——不存在「内网调用免检」的口子。
 *
 * <p>注意这个类放在主容器而不是 {@code @FeignClient(configuration=...)}：
 * Feign 子上下文拿不到主容器的 {@code JwtTokenService}。主容器里的
 * {@link RequestInterceptor} 对全部 Feign 客户端生效。
 */
@Configuration
public class FeignAuthConfig {

    private static final LoginUser SYSTEM_USER = new LoginUser(0L, "system-scheduler", List.of("SYSTEM"));

    @Bean
    public RequestInterceptor authorizationRelayInterceptor(JwtTokenService jwtTokenService) {
        return template -> {
            String authorization = currentAuthorization();
            if (authorization == null) {
                authorization = "Bearer " + jwtTokenService.issue(SYSTEM_USER);
            }
            template.header(HttpHeaders.AUTHORIZATION, authorization);
        };
    }

    private String currentAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }
}
