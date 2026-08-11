package com.banksystem.customer.infrastructure.kyc;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.application.kyc.KycObjectStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioKycObjectStorage implements KycObjectStorage {

  private final MinioClient client;
  private final String bucket;

  public MinioKycObjectStorage(
      @Value("${bank.kyc.storage.endpoint}") String endpoint,
      @Value("${bank.kyc.storage.access-key}") String accessKey,
      @Value("${bank.kyc.storage.secret-key}") String secretKey,
      @Value("${bank.kyc.storage.bucket}") String bucket) {
    this.client = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build();
    this.bucket = bucket;
  }

  @Override
  public void put(String objectKey, InputStream content, long size, String contentType) {
    try {
      ensureBucket();
      client.putObject(PutObjectArgs.builder()
          .bucket(bucket)
          .object(objectKey)
          .contentType(contentType)
          .stream(content, size, -1)
          .build());
    } catch (Exception ex) {
      throw new BusinessException("KYC_STORAGE_FAILED", "Cannot store KYC document");
    }
  }

  @Override
  public StoredObject get(String objectKey) {
    try {
      GetObjectResponse response = client.getObject(GetObjectArgs.builder()
          .bucket(bucket).object(objectKey).build());
      String contentType = response.headers().get("Content-Type");
      String contentLength = response.headers().get("Content-Length");
      long size = contentLength == null ? -1 : Long.parseLong(contentLength);
      return new StoredObject(response, contentType, size);
    } catch (Exception ex) {
      throw new BusinessException("KYC_DOCUMENT_UNAVAILABLE", "Cannot read KYC document");
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception ex) {
      throw new BusinessException("KYC_STORAGE_FAILED", "Cannot delete KYC document");
    }
  }

  private void ensureBucket() throws Exception {
    boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    if (!exists) {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
  }
}
