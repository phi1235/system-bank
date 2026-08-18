package com.banksystem.transaction.infrastructure.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.forensics.ForensicArtifactStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank.forensics.storage.type", havingValue = "minio")
public class MinioForensicArtifactStorage implements ForensicArtifactStorage {
  private static final String URI_PREFIX = "minio://";
  private final MinioClient client;
  private final String bucket;

  public MinioForensicArtifactStorage(
      @Value("${bank.forensics.storage.endpoint}") String endpoint,
      @Value("${bank.forensics.storage.access-key}") String accessKey,
      @Value("${bank.forensics.storage.secret-key}") String secretKey,
      @Value("${bank.forensics.storage.bucket}") String bucket) {
    this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    this.bucket = bucket;
  }

  @Override
  public String put(String objectKey, byte[] content, String contentType) {
    try {
      ensureBucket();
      client.putObject(PutObjectArgs.builder()
          .bucket(bucket).object(objectKey).contentType(contentType)
          .stream(new ByteArrayInputStream(content), content.length, -1).build());
      return URI_PREFIX + bucket + "/" + objectKey;
    } catch (Exception exception) {
      throw new BusinessException("FORENSIC_STORAGE_FAILED", "Cannot store forensic artifact");
    }
  }

  @Override
  public StoredArtifact get(String storageUri) {
    String objectKey = objectKey(storageUri);
    try (GetObjectResponse response = client.getObject(
        GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
      String contentType = response.headers().get("Content-Type");
      return new StoredArtifact(response.readAllBytes(), contentType);
    } catch (Exception exception) {
      throw new BusinessException("FORENSIC_ARTIFACT_UNAVAILABLE", "Cannot read forensic artifact");
    }
  }

  @Override
  public void delete(String storageUri) {
    try {
      client.removeObject(RemoveObjectArgs.builder()
          .bucket(bucket).object(objectKey(storageUri)).build());
    } catch (Exception exception) {
      throw new BusinessException("FORENSIC_STORAGE_FAILED", "Cannot delete forensic artifact");
    }
  }

  private String objectKey(String storageUri) {
    String expected = URI_PREFIX + bucket + "/";
    if (storageUri == null || !storageUri.startsWith(expected)) {
      throw new BusinessException("FORENSIC_STORAGE_URI_INVALID", "Invalid forensic storage URI");
    }
    return storageUri.substring(expected.length());
  }

  private void ensureBucket() throws Exception {
    if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
  }
}
