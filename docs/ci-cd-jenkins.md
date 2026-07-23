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

## Create the pipeline job (recommended: on-demand branch)

**Do NOT use Multibranch** if you want to type a branch and build only that ref.
Multibranch with “Discover branches: All branches” will create a job per branch and often builds many at once (heavy on RAM).

### Recommended — single Pipeline + `BRANCH_NAME` parameter

1. If you already created Multibranch `bank-system`: **Disable** or **Delete** it (or turn off scan triggers) so it stops auto-building.
2. **New Item** → **Pipeline** → name e.g. `system-bank-build`
3. Pipeline → Definition: **Pipeline script from SCM**
   - SCM: **Git**
   - URL: `https://github.com/phi1235/system-bank.git`
   - Credentials: `github-pat` (Username + PAT)
   - Branch Specifier: `*/main` **or** `*/feature/jenkins-local-ci`  
     (this branch only needs to contain the *Jenkinsfile script*; the pipeline then checks out **`params.BRANCH_NAME`** for the actual build)
   - Script path: `Jenkinsfile`
4. Save
5. **Build with Parameters**:
   - `BRANCH_NAME` = nhánh anh muốn (vd `main`, `feature/transfer-fee-gl`)
   - `RUN_PACKAGE` = false (phase 1)
   - `DEPLOY_ENABLED` = false
6. Build — **only that branch** runs (one Maven verify + one FE build, not N branches × services)

Credential ID in `Jenkinsfile` checkout is `github-pat`. Create Jenkins credential with **exactly that ID** (or change the ID in Jenkinsfile to match yours).

### Optional — Multibranch (only if you really want auto per-branch)

1. **New Item** → Multibranch Pipeline
2. Branch source: Git / GitHub + credentials
3. **Behaviours** — do **not** leave “All branches” without a filter:
   - Add **Filter by name (with regular expression)**: e.g. `main` only, or `main|develop`
   - Or add **Build strategies** → suppress automatic SCM triggering (scan without build)
4. Limit executors (Manage Jenkins → Nodes → Built-in → # of executors = **1**) so even Multibranch cannot run two heavy builds in parallel

### GitHub webhook (optional on local machine)

Local Jenkins is not reachable from GitHub cloud without a tunnel (ngrok/cloudflared). For on-demand local builds:

- Use **Build with Parameters** only (recommended for your machine)

Phase 2 on a VPS: add webhook if you want push-to-build for a *filtered* set of branches.

## Pipeline stages (phase 1)

1. **Checkout** — clone **only** `params.BRANCH_NAME` (not every branch)  
2. **Secrets check** — fail if `.env` / `infra/.env` is tracked  
3. **Backend verify** — `maven:3.9.9-eclipse-temurin-21` → `mvn -B -q verify` in `backend/`  
4. **Frontend lint & build** — `node:20-bookworm` → `npm ci` + `lint` + `build`  
5. **Package images** — only if param `RUN_PACKAGE=true`  
6. **Deploy** — **blocked** unless phase 2 is configured; param `DEPLOY_ENABLED` still errors on purpose  

### Parameters

| Param | Default | Meaning |
|-------|---------|---------|
| `BRANCH_NAME` | `main` | **Nhánh anh nhập** — chỉ build ref này |
| `RUN_PACKAGE` | `false` | Build service images locally (no push) |
| `DEPLOY_ENABLED` | `false` | Phase 2 only |

Also set job/env `GIT_CREDENTIALS_ID` if your credential ID is not `github-pat`.

### Why not Multibranch for local Docker?

- Multibranch + “All branches” → scan tạo job + build **nhiều nhánh song song**.
- Mỗi build = full `mvn verify` + Angular (nặng). N nhánh ≈ N× tải máy.
- On-demand Pipeline: **1 executor, 1 branch/lần**, anh chọn khi bấm Build.

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
