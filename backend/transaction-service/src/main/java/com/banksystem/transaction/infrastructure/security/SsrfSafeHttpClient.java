package com.banksystem.transaction.infrastructure.security;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SsrfSafeHttpClient {

  private static final Logger log = LoggerFactory.getLogger(SsrfSafeHttpClient.class);

  private final HttpClient httpClient;

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  public SsrfSafeHttpClient() {
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  public HttpResponseDto sendWebhook(String targetUrl, String payload, String signature, long timestamp) throws IOException, InterruptedException {
    validateUrl(targetUrl);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(targetUrl))
        .timeout(Duration.ofSeconds(5))
        .header("Content-Type", "application/json; charset=UTF-8")
        .header("X-Bank-Timestamp", String.valueOf(timestamp))
        .header("X-Bank-Signature", signature)
        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    String body = response.body();
    if (body != null && body.length() > 1000) {
      body = body.substring(0, 1000);
    }
    return new HttpResponseDto(response.statusCode(), body);
  }

  public void validateUrl(String urlStr) {
    if (urlStr == null || urlStr.length() > 2048) {
      throw new IllegalArgumentException("Webhook URL is null or exceeds 2048 characters");
    }

    URI uri = URI.create(urlStr);
    String scheme = uri.getScheme();
    if (scheme == null) {
      throw new IllegalArgumentException("Invalid URL scheme");
    }

    boolean isProd = activeProfile != null && activeProfile.contains("prod");
    if (isProd && !"https".equalsIgnoreCase(scheme)) {
      throw new IllegalArgumentException("In production, webhook URLs must strictly use HTTPS protocol");
    }

    if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
      throw new IllegalArgumentException("Unsupported URL scheme: " + scheme);
    }

    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Invalid host in URL");
    }

    // SSRF & DNS Rebinding guard across all environments
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      if (addresses.length == 0) {
        throw new IllegalArgumentException("Could not resolve any IP for webhook host: " + host);
      }
      for (InetAddress addr : addresses) {
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
            || addr.isAnyLocalAddress() || addr.isMulticastAddress()
            || isRestrictedIp(addr.getHostAddress())) {
          log.error("[SSRF-BLOCK] Blocked webhook attempt to private/internal IP: {} ({})", host, addr.getHostAddress());
          throw new IllegalArgumentException("Webhook URL targets a restricted private network address: " + addr.getHostAddress());
        }
      }
    } catch (Exception e) {
      if (e instanceof IllegalArgumentException iae) throw iae;
      log.warn("[SSRF-GUARD] DNS resolution failure for host {}: {}", host, e.getMessage());
      throw new IllegalArgumentException("Could not resolve webhook host: " + host);
    }
  }

  private boolean isRestrictedIp(String ip) {
    if (ip == null) return true;
    return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("169.254.")
        || ip.startsWith("127.") || ip.startsWith("0.") || ip.equals("::1") || ip.startsWith("fc") || ip.startsWith("fd");
  }

  public record HttpResponseDto(int statusCode, String body) {}
}
