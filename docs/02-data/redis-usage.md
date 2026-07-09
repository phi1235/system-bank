# Redis Usage

## Roles

1. Refresh token store / rotation
2. Access token blacklist (logout)
3. Login fail counter (brute-force)
4. MFA setup temporary secret
5. (Optional) Gateway rate limiter

## Key design

Prefix: `bank:`

| Key | Value | TTL |
|-----|-------|-----|
| `bank:auth:refresh:{jti}` | userId | 7d |
| `bank:auth:bl:{jti}` | "1" | access remaining |
| `bank:auth:fail:{ip}` | int | 15m |
| `bank:mfa:setup:{userId}` | encrypted secret | 10m |

## Client

Spring Data Redis / Lettuce.  
Config: `REDIS_HOST`, `REDIS_PORT`, optional password.

## Failure mode

- Redis down → auth refresh/logout degraded; **fail closed** cho refresh (503)  
- Login có thể fallback chỉ DB password check + in-memory fail count **không** — keep simple: require Redis healthy in compose
