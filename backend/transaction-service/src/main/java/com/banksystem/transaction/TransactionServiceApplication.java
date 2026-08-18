package com.banksystem.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = "com.banksystem")
@EnableFeignClients
@EnableScheduling
@EnableAsync
@EnableCaching
public class TransactionServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(TransactionServiceApplication.class, args);
  }
}
