package com.banksystem.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.banksystem")
@EnableFeignClients
public class CustomerServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(CustomerServiceApplication.class, args);
  }
}
