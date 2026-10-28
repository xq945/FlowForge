package com.flowforge.execution;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.flowforge.execution.mapper")
public class FlowForgeExecutionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowForgeExecutionApplication.class, args);
    }
}
