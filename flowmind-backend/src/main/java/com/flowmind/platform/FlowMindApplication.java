package com.flowmind.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@MapperScan(basePackages = "com.flowmind.platform", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class FlowMindApplication {
    public static void main(String[] args) { SpringApplication.run(FlowMindApplication.class, args); }
}
