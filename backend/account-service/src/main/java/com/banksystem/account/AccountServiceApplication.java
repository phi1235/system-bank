package com.banksystem.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.banksystem")
@EnableFeignClients
@EnableScheduling
public class AccountServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(AccountServiceApplication.class, args);
  }
}
