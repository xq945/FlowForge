package com.flowforge.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 各服务额外放行的路径（Ant 风格）。
 *
 * <p>这里**只放业务自己的公开接口**，如注册、登录。
 * 健康检查与接口文档属于框架级公共路径，由 {@link FlowForgeSecurityAutoConfiguration}
 * 在装配时合并进来。
 *
 * <p>注意不能把默认值写在这个字段上：Spring Boot 绑定集合属性时会先清空再填充，
 * 字段里的默认值会被配置文件整个覆盖掉——这正是最初 {@code /actuator/health}
 * 意外返回 401 的原因。
 */
@ConfigurationProperties(prefix = "flowforge.security")
public class SecurityWhitelistProperties {

    private List<String> whitelist = new ArrayList<>();

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist == null ? new ArrayList<>() : whitelist;
    }
}
