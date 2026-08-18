package com.banksystem.transaction.application.forensics;

public interface ForensicArtifactStorage {
  String put(String objectKey, byte[] content, String contentType);
  StoredArtifact get(String storageUri);
  void delete(String storageUri);

  record StoredArtifact(byte[] content, String contentType) {}
}
