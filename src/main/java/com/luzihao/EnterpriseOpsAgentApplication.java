package com.luzihao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.luzihao"})
@EnableAsync
public class EnterpriseOpsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseOpsAgentApplication.class, args);
    }

}
