# CI/CD — Jenkins (local Docker) + GitHub Actions hybrid

## Decisions (2026-07-23)

| Item | Choice |
|------|--------|
| Jenkins host | **Local Docker** on developer machine |
| Method | **B — hybrid** |
| Phase 1 | Build / test / optional image package |
| Phase 2 | Registry push + VPS deploy (not enabled yet) |

### Split of responsibility

| Layer | Tool | Scope |
|-------|------|--------|
| Light PR gate | GitHub Actions (`.github/workflows/ci.yml`) | Secrets check, backend compile (skipTests), FE lint |
| Heavy CI | Jenkins (`Jenkinsfile`) | Full `mvn verify`, FE lint+production build, optional Docker package |
| Deploy | Jenkins (later) | Push images + SSH/compose — **phase 2** |

## Prerequisites

- Docker Desktop (Windows) or Docker Engine with Compose v2
- Ports free: **18080** (Jenkins UI), **50000** (inbound agents, optional)
- Repo clone: `C:\Users\TuanNQ3\system-bank` (or any path)

## Start Jenkins locally

From **repo root**:

```bash
docker compose -f infra/jenkins/docker-compose.yml up -d --build
```

Image build installs plugins from `plugins.txt` and Docker CLI (uses host engine via socket).

Open: [http://localhost:18080](http://localhost:18080)

### First-time unlock

```bash
docker exec system-bank-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

1. Paste password → Install suggested plugins (or use `infra/jenkins/plugins.txt` later).
2. Create local admin user (do **not** expose this UI to the public internet without reverse proxy + auth).
3. Optional JCasC: after wizard, set env `CASC_JENKINS_CONFIG=/casc/casc.yaml` (file mounted from `infra/jenkins/casc.yaml`) and restart the container.

### Install recommended plugins

UI: **Manage Jenkins → Plugins**, or copy `plugins.txt` into a custom image later. Minimum set:

- Pipeline / workflow-aggregator  
- Git, GitHub, GitHub Branch Source  
- Docker Pipeline  
- Credentials Binding  
- Timestamper, AnsiColor, Workspace Cleanup  
- Configuration as Code  

Restart Jenkins after plugin install.

## Create the pipeline job

### Option A — Pipeline from SCM (simplest)

1. **New Item** → Pipeline → name `system-bank`
2. Pipeline → Definition: **Pipeline script from SCM**
3. SCM: **Git**
   - URL: `https://github.com/phi1235/system-bank.git`
   - Credentials: none if public; else `github-pat`
   - Branch: `*/main` or `*/feature/jenkins-local-ci` while testing
4. Script path: `Jenkinsfile`
5. Save → **Build Now**

### Option B — Multibranch (recommended after first green build)

1. **New Item** → Multibranch Pipeline
2. Branch source: GitHub (or Git)
3. Discover branches / PRs
4. Build configuration: by `Jenkinsfile`

### GitHub webhook (optional on local machine)

Local Jenkins is not reachable from GitHub cloud without a tunnel (ngrok/cloudflared). For local-only:

- Trigger **Build Now** manually, or  
- Use **GitHub → ngrok → Jenkins** when you want push-to-build

Phase 2 on a VPS: add webhook  
`http://<jenkins-host>:8080/github-webhook/` with secret.

## Pipeline stages (phase 1)

1. **Checkout**  
2. **Secrets check** — fail if `.env` / `infra/.env` is tracked  
3. **Backend verify** — `maven:3.9.9-eclipse-temurin-21` → `mvn -B -q verify` in `backend/`  
4. **Frontend lint & build** — `node:20-bookworm` → `npm ci` + `lint` + `build`  
5. **Package images** — only if param `RUN_PACKAGE=true` (or env `PACKAGE_ON_MAIN=true` on `main`)  
6. **Deploy** — **blocked** unless phase 2 is configured; param `DEPLOY_ENABLED` still errors on purpose  

### Parameters

| Param | Default | Meaning |
|-------|---------|---------|
| `RUN_PACKAGE` | `false` | Build service images locally (no push) |
| `DEPLOY_ENABLED` | `false` | Phase 2 only |

## GitHub Actions (light gate)

File: `.github/workflows/ci.yml`

- Still runs on PR / push to `main` (and `develop`/`master` if used)
- Backend: **compile only** (`-DskipTests`) for speed  
- Frontend: **lint only** (no full production build on GHA free minutes)  
- Secrets check retained  

Heavy tests and image builds stay on Jenkins.

## Credentials inventory (create in Jenkins UI)

| ID (suggested) | Type | Phase |
|----------------|------|-------|
| `github-pat` | Secret text / username+token | 1 if private clone |
| `docker-registry` | Username + password | 2 push |
| `deploy-ssh` | SSH private key | 2 VPS |
| `notify-telegram` / Slack | Secret text | 2 notify |

Never commit secrets. Keep real values out of `casc.yaml`.

## Phase 2 checklist (later)

- [ ] Move Jenkins to VPS or keep local runner with stable tunnel  
- [ ] Registry (GHCR/Docker Hub) + `docker-registry` credential  
- [ ] Replace Deploy stage: `docker push` + `ssh` compose pull/up  
- [ ] Branch/tag guards + manual approval  
- [ ] Slack/Telegram notify on failure/success  
- [ ] Optional: drop GHA entirely or keep as light only  

## Ops commands

```bash
# Start / stop
docker compose -f infra/jenkins/docker-compose.yml up -d
docker compose -f infra/jenkins/docker-compose.yml down

# Logs
docker logs -f system-bank-jenkins

# Reset Jenkins home (destructive)
docker compose -f infra/jenkins/docker-compose.yml down -v
```

## Layout

```
system-bank/
├── Jenkinsfile
├── docs/ci-cd-jenkins.md
├── .github/workflows/ci.yml    # light gate
└── infra/jenkins/
    ├── Dockerfile              # LTS + plugins + docker CLI
    ├── docker-compose.yml
    ├── plugins.txt
    └── casc.yaml
```

## Notes / caveats

- **Windows Docker socket**: compose uses `//var/run/docker.sock` for Desktop Linux VM. If package stage cannot talk to Docker, run package on a Linux agent or fix socket mount.  
- **Resource**: full `mvn verify` + Angular build needs free RAM/CPU; close heavy apps if OOM.  
- **Security**: local Jenkins with Docker socket is powerful (root on host Docker). Do not publish port 18080 beyond localhost without hardening.  
