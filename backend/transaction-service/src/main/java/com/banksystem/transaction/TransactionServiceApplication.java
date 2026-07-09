package com.banksystem.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.banksystem")
@EnableFeignClients
@EnableScheduling
public class TransactionServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(TransactionServiceApplication.class, args);
  }
}
