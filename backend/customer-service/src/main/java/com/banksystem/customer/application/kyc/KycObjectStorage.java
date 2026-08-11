package com.banksystem.customer.application.kyc;

import java.io.InputStream;

public interface KycObjectStorage {
  void put(String objectKey, InputStream content, long size, String contentType);
  StoredObject get(String objectKey);
  void delete(String objectKey);

  record StoredObject(InputStream content, String contentType, long size) {}
}
