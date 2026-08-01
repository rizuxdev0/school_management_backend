package com.schoolmanager.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAsync
@EnableAspectJAutoProxy
public class SystemConfigApplication {
    public static void main(String[] args) {
        SpringApplication.run(SystemConfigApplication.class, args);
    }
}
