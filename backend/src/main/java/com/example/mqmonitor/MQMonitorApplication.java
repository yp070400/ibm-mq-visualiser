package com.example.mqmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.mqmonitor.config.MonitorProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MonitorProperties.class)
public class MQMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MQMonitorApplication.class, args);
    }
}