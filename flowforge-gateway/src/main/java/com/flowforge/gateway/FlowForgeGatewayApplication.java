package com.flowforge.gateway;

import com.flowforge.gateway.config.GatewaySecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class FlowForgeGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowForgeGatewayApplication.class, args);
    }
}
