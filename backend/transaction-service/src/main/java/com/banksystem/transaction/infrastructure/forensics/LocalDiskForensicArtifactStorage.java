package com.banksystem.transaction.infrastructure.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.forensics.ForensicArtifactStorage;
import com.banksystem.transaction.application.forensics.ForensicArtifactStorage.StoredArtifact;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank.forensics.storage.type", havingValue = "disk", matchIfMissing = true)
public class LocalDiskForensicArtifactStorage implements ForensicArtifactStorage {
  private static final String URI_PREFIX = "file://";
  private final Path storageDir;

  public LocalDiskForensicArtifactStorage(
      @Value("${bank.forensics.storage.disk-path:./storage/forensics}") String diskPath) {
    this.storageDir = Paths.get(diskPath).toAbsolutePath().normalize();
    ensureDirectory();
  }

  @Override
  public String put(String objectKey, byte[] content, String contentType) {
    try {
      ensureDirectory();
      Path targetPath = storageDir.resolve(objectKey).normalize();
      if (!targetPath.startsWith(storageDir)) {
        throw new BusinessException("FORENSIC_STORAGE_PATH_INVALID", "Path traversal detected");
      }
      Files.createDirectories(targetPath.getParent());
      Files.write(targetPath, content);
      return URI_PREFIX + targetPath.toString().replace(File.separatorChar, '/');
    } catch (IOException exception) {
      throw new BusinessException("FORENSIC_STORAGE_FAILED", "Cannot store artifact on local disk");
    }
  }

  @Override
  public StoredArtifact get(String storageUri) {
    Path targetPath = parseUri(storageUri);
    if (!Files.exists(targetPath)) {
      throw new BusinessException("FORENSIC_ARTIFACT_UNAVAILABLE", "Artifact file does not exist");
    }
    try {
      byte[] bytes = Files.readAllBytes(targetPath);
      String contentType = targetPath.toString().endsWith(".json") ? "application/json" : "application/octet-stream";
      return new StoredArtifact(bytes, contentType);
    } catch (IOException exception) {
      throw new BusinessException("FORENSIC_ARTIFACT_UNAVAILABLE", "Cannot read artifact from local disk");
    }
  }

  @Override
  public void delete(String storageUri) {
    Path targetPath = parseUri(storageUri);
    try {
      Files.deleteIfExists(targetPath);
    } catch (IOException exception) {
      throw new BusinessException("FORENSIC_STORAGE_FAILED", "Cannot delete artifact from local disk");
    }
  }

  private Path parseUri(String storageUri) {
    if (storageUri == null || !storageUri.startsWith(URI_PREFIX)) {
      throw new BusinessException("FORENSIC_STORAGE_URI_INVALID", "Invalid local storage URI");
    }
    String pathStr = storageUri.substring(URI_PREFIX.length());
    return Paths.get(pathStr).normalize();
  }

  private void ensureDirectory() {
    try {
      if (!Files.exists(storageDir)) {
        Files.createDirectories(storageDir);
      }
    } catch (IOException exception) {
      throw new BusinessException("FORENSIC_STORAGE_FAILED", "Cannot initialize local storage directory");
    }
  }
}
