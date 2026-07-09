package com.banksystem.auth.infrastructure.jwt;

import com.banksystem.auth.domain.UserEntity;
import com.banksystem.common.security.SecurityHeaders;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  public static final String TYPE_ACCESS = "access";
  public static final String TYPE_REFRESH = "refresh";
  public static final String TYPE_MFA = "mfa_pending";

  private final JwtProperties props;
  private final SecretKey key;

  public JwtService(JwtProperties props) {
    this.props = props;
    byte[] bytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
    this.key = Keys.hmacShaKeyFor(bytes.length >= 32 ? bytes : pad(bytes));
  }

  public TokenPair issueSessionTokens(UserEntity user) {
    String accessJti = UUID.randomUUID().toString();
    String refreshJti = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Instant accessExp = now.plusSeconds(props.getAccessTtlSeconds());
    Instant refreshExp = now.plusSeconds(props.getRefreshTtlSeconds());

    String access = buildToken(user, accessJti, TYPE_ACCESS, accessExp, now);
    String refresh = buildToken(user, refreshJti, TYPE_REFRESH, refreshExp, now);
    return new TokenPair(access, refresh, accessJti, refreshJti, props.getAccessTtlSeconds(),
        props.getRefreshTtlSeconds());
  }

  public String issueMfaToken(UserEntity user) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(props.getMfaTtlSeconds());
    return buildToken(user, UUID.randomUUID().toString(), TYPE_MFA, exp, now);
  }

  public Claims parse(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public boolean isType(Claims claims, String type) {
    return type.equals(claims.get(SecurityHeaders.JWT_CLAIM_TYPE, String.class));
  }

  public long remainingTtlSeconds(Claims claims) {
    Date exp = claims.getExpiration();
    if (exp == null) {
      return 0;
    }
    long secs = (exp.getTime() - System.currentTimeMillis()) / 1000;
    return Math.max(secs, 1);
  }

  private String buildToken(UserEntity user, String jti, String type, Instant exp, Instant now) {
    List<String> roles = user.roleList();
    String realm = roles.stream().anyMatch(r -> r.equalsIgnoreCase("ADMIN") || r.contains("ADMIN"))
        ? "BACK_OFFICE"
        : "INTERNET_BANKING";

    return Jwts.builder()
        .id(jti)
        .subject(user.getId().toString())
        .claim("username", user.getUsername())
        .claim(SecurityHeaders.JWT_CLAIM_ROLES, roles)
        .claim(SecurityHeaders.JWT_CLAIM_TYPE, type)
        .claim(SecurityHeaders.JWT_CLAIM_REALM, realm)
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(key)
        .compact();
  }

  private static byte[] pad(byte[] key) {
    byte[] padded = new byte[32];
    System.arraycopy(key, 0, padded, 0, Math.min(key.length, 32));
    return padded;
  }

  public record TokenPair(
      String accessToken,
      String refreshToken,
      String accessJti,
      String refreshJti,
      long accessTtlSeconds,
      long refreshTtlSeconds
  ) {}
}
