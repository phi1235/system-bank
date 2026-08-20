package com.banksystem.transaction.config;

import com.banksystem.transaction.infrastructure.security.MerchantApiAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final MerchantApiAuthInterceptor merchantApiAuthInterceptor;

  public WebMvcConfig(MerchantApiAuthInterceptor merchantApiAuthInterceptor) {
    this.merchantApiAuthInterceptor = merchantApiAuthInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(merchantApiAuthInterceptor)
        .addPathPatterns("/api/v1/merchant/**");
  }
}
