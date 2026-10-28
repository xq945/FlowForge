package com.flowforge.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * 无状态 JWT 安全链的公共装配。
 *
 * <p>只依赖「这是不是 Servlet 应用 + classpath 有没有 Spring Security」两个条件，
 * 任何业务服务引入 starter-security 后自动获得：JWT 认证、统一 401/403 输出、密码编码器。
 * 放行路径通过 {@code flowforge.security.whitelist} 追加。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, SecurityWhitelistProperties.class})
public class FlowForgeSecurityAutoConfiguration {

    /** 框架级公共路径：健康检查与接口文档不需要登录 */
    private static final List<String> FRAMEWORK_PUBLIC_PATHS = List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenService jwtTokenService(JwtProperties properties) {
        return new JwtTokenService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonAuthErrorHandler jsonAuthErrorHandler(ObjectMapper objectMapper) {
        return new JsonAuthErrorHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        // 自适应哈希，强度 10：约 50~80ms/次，足以抵御离线暴力破解又不拖慢登录
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain flowForgeSecurityFilterChain(HttpSecurity http,
                                                            JwtTokenService jwtTokenService,
                                                            JsonAuthErrorHandler authErrorHandler,
                                                            SecurityWhitelistProperties whitelist) throws Exception {
        List<String> merged = new ArrayList<>(FRAMEWORK_PUBLIC_PATHS);
        merged.addAll(whitelist.getWhitelist());
        String[] publicPaths = merged.toArray(String[]::new);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authErrorHandler)
                        .accessDeniedHandler(authErrorHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
