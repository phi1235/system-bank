package com.banksystem.account.config;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
