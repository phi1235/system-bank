# CI/CD — GitHub Actions

## Path

`.github/workflows/ci.yml`

## On

push/PR to `main`, `develop`, `master`

## Jobs

### backend
- Setup JDK 21 (Temurin) + Maven cache
- `cd backend && mvn -B -q verify`
- Fail on test failure

### frontend
- Node 20 + npm cache
- `cd frontend/bank-angular-app`
- `npm ci`
- `npm run lint` (`tsc --noEmit`)
- `npm run build`

### secrets-check
- Fail if any tracked `.env` / `infra/.env`

## Quality gates MVP

- Compile + unit tests
- FE typecheck + production build
- No committed secrets

## Not in MVP

- CD to cloud
- K8s deploy
- Image push to registry
- Sonar / paid SAST
