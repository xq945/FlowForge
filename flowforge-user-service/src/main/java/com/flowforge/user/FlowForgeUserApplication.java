package com.flowforge.user;

import com.flowforge.user.config.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(JwtProperties.class)
@MapperScan("com.flowforge.user.mapper")
public class FlowForgeUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowForgeUserApplication.class, args);
    }
}
