package com.flowforge.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.flowforge.workflow.mapper")
public class FlowForgeWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowForgeWorkflowApplication.class, args);
    }
}
