# Branching strategy

## Environments & long-lived branches

| Branch | Role | Notes |
|--------|------|--------|
| **`main`** | **STG** (staging) | Stable integration branch. Deploy STG from here. |
| **`uat/v1.0.0`** | **UAT / release line v1.0.0** | Features and fixes for version 1.0.0 land here first (via feature branches). |

Future UAT lines follow the same pattern: `uat/v1.1.0`, `uat/v2.0.0`, …

## Day-to-day workflow

```
feature work          merge request              promote when ready
─────────────         ─────────────              ─────────────────
feat/...  ──MR──►  uat/v1.0.0  ──MR──►  main (STG)
fix/...   ──MR──►  uat/v1.0.0  ──MR──►  main (STG)
```

1. **Start from UAT line** (not raw `main` for feature work):
   ```bash
   git fetch origin
   git checkout uat/v1.0.0
   git pull origin uat/v1.0.0
   git checkout -b feat/short-description
   # or: fix/short-description
   ```

2. **Open Merge Request** into `uat/v1.0.0` when the feature/fix is ready for UAT.

3. **After UAT is good**, open **MR from `uat/v1.0.0` → `main`** (STG) to promote the release line.

4. Prefer **no direct push** to `main` for features; use MRs so history stays reviewable.

## Branch naming

| Prefix | Use |
|--------|-----|
| `feat/` | New feature |
| `fix/` | Bug fix |
| `chore/` | Tooling, docs, deps (non-product) |
| `uat/vX.Y.Z` | Long-lived UAT / release line |

Examples: `feat/transfer-receipt`, `fix/login-rate-limit`.

## Tags (optional release marks)

After a successful promote to STG / production cut:

```bash
git checkout main
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```

## Summary

- **`main` = STG**  
- **`uat/v1.0.0` = UAT development line for 1.0.0**  
- **Feature/fix branches → MR → `uat/v1.0.0` → MR → `main`**
