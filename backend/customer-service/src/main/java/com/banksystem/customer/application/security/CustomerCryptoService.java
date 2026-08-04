package com.banksystem.customer.application.security;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import com.banksystem.common.security.CryptoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CustomerCryptoService {

  private final String aesKey;

  public CustomerCryptoService(@Value("${bank.aes.secret-key}") String aesKey) {
    this.aesKey = aesKey;
  }

  public String encryptNationalId(String rawNationalId) {
    if (rawNationalId == null || rawNationalId.isBlank()) {
      return null;
    }
    return CryptoUtils.encrypt(rawNationalId.trim(), aesKey);
  }

  public String decryptAndMaskNationalId(String encryptedNationalId) {
    if (encryptedNationalId == null || encryptedNationalId.isBlank()) {
      return null;
    }
    try {
      String plain = CryptoUtils.decrypt(encryptedNationalId, aesKey);
      return CryptoUtils.maskNationalId(plain);
    } catch (Exception ex) {
      return "****";
    }
  }
}
