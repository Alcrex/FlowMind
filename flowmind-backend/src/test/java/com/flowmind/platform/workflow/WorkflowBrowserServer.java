package com.flowmind.platform.workflow;

import com.flowmind.platform.FlowMindApplication;
import org.springframework.boot.SpringApplication;

/** Isolated browser server with the production controllers and in-memory test database. */
public class WorkflowBrowserServer {
    public static void main(String[] args) {
        var application = new SpringApplication(FlowMindApplication.class);
        application.setAdditionalProfiles("test");
        application.run(args);
    }
}
