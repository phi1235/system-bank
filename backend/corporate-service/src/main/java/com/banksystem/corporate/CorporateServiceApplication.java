package com.banksystem.corporate;

import io.minio.MinioClient;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.banksystem.common", "com.banksystem.corporate"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class CorporateServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(CorporateServiceApplication.class, args);
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public MinioClient minioClient(
      @Value("${bank.storage.endpoint}") String endpoint,
      @Value("${bank.storage.access-key}") String accessKey,
      @Value("${bank.storage.secret-key}") String secretKey) {
    return MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build();
  }
}
