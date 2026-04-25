---
category: reference
status: active
last_verified: 2026-04-24
---
# Firebase Server Operations

Long-term maintenance guide for the Firebase Cloud Functions that back the Smart Garage Door app. Covers deploy, rollback, monitoring, cost hygiene, and recovery of the CI pipeline.

**No secrets in this document.** Only the structure and the commands to re-provision. Actual credentials live in GitHub Actions secrets and are never checked into git.

---

## Status

As of **2026-04-22**:

- ✅ **Manual deploy (local)** — verified. `firebase deploy --only functions` works from a developer machine logged in as the project owner.
- ✅ **CI-automated deploy (GitHub Actions)** — verified end-to-end 2026-04-21 after re-provisioning the deployer service account. Both manual-rerun and fresh-tag-push trigger paths are known good (`server/4` rerun and `server/5` fresh-push both succeeded).
- ✅ **Production:** `server/5` (same code as `server/4` — jws security override applied, Dependabot alerts #25, #26 closed).
- ⚠️ **Historical note:** pre-2026-04-21 CI deploys never actually deployed. `firebase deploy` was exiting 0 despite a `⚠ failed to update function` warning, so GitHub Actions showed "success" but production never updated. Every real deploy before 2026-04-21 was manual. This was fixed by creating `github-actions-deploy@escape-echo.iam.gserviceaccount.com` with the correct IAM roles and rotating the `FIREBASE_SERVICE_ACCOUNT` secret.

---

## Environment model

Single-environment: there is **no staging**. Every change goes direct to production.

- **Project:** `escape-echo`
- **Project number:** `587667443259`
- **Region:** `us-central1`
- **Runtime:** Node.js 22 (pinned in `FirebaseServer/.nvmrc` and `FirebaseServer/package.json` `engines.node`). Migrated from Node 20 in `server/10`.
- **Function generation:** Cloud Functions **1st Gen** (18 functions, mix of HTTPS + Pub/Sub + Firestore triggers)

If a staging environment is ever added, it'd need its own project ID, its own deployer SA key, and its own `FIREBASE_PROJECT_ID` secret scoped via environment.

---

## Release process

### Cutting a release

Use the release script (hooks block direct `git tag`):

```bash
./scripts/release-firebase.sh --check           # preview next tag
./scripts/release-firebase.sh --confirm-tag server/N
```

The script:
1. Verifies clean git state on `main`
2. Verifies Firebase CI passed on HEAD (or warns if CI didn't run because changes were docs-only)
3. Creates and pushes tag `server/N`
4. The tag push triggers `.github/workflows/firebase-deploy.yml` automatically

### Verifying a release landed

A deploy succeeds when the workflow log shows all functions with a ✔ and ends with `Deploy complete!`:

```
✔ functions[echo(us-central1)]                    Successful update operation.
✔ functions[remoteButton(us-central1)]            Successful update operation.
... (17 total)
✔ Deploy complete!
```

Smoke check from any terminal (expected: `401 Unauthorized` — handler reachable, rejected because no `X-ServerConfigKey` header sent):

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://us-central1-escape-echo.cloudfunctions.net/serverConfig
```

Any `5xx` or connection-refused = something's wrong.

### ⚠️ The silent-failure pattern to watch for

Before 2026-04-21, `firebase deploy` would exit 0 despite printing `⚠ functions: failed to update function <name>` — the workflow "succeeded" but nothing deployed. **Always look for `✔ Deploy complete!` in the log before trusting a "success" conclusion.** If you see the `⚠ failed to update` warning pattern return, the deployer service account has lost a role (see Troubleshooting).

---

## Rollback

There is no automatic rollback. To revert production to a prior `server/N`:

### Option 1 — re-deploy an old tag (simplest)

```bash
# Checkout the tag locally (read-only; don't push from here)
git checkout server/<old-N>

# Deploy from the tagged state
firebase deploy --only functions --project escape-echo --config FirebaseServer/firebase.json

# Back to main when done
git checkout main
```

This re-pushes the old code to Cloud Functions. It does NOT move the `server/<new-N>` tag. Production is now running the old code; git history is unchanged. This is safe because Cloud Functions just accepts whatever is uploaded — there's no tag-to-version binding at the runtime layer.

### Option 2 — cut a revert release

Revert the bad commit on main, bump version, and cut a new tag:

```bash
git revert <bad-commit-sha>
git push origin main
./scripts/release-firebase.sh --confirm-tag server/<next-N>
```

Slower but leaves a clean git history and a tagged release.

### Option 3 — emergency: use Cloud Functions Console

Each function has a "Versions" list in the GCP Console ([functions list](https://console.cloud.google.com/functions/list?project=escape-echo)). You can manually roll an individual function back to an earlier `versionId` from the UI without touching git. Useful if only one function is broken, but fragmented — easy to forget which function ended up on which version.

### Never do

- `git tag -d server/N && git push --delete origin server/N` — deleting a tag breaks audit trail and doesn't roll back production. The running Cloud Functions remain on whatever was last deployed.

---

## Monitoring and logs

### Function logs (day-to-day)

```bash
firebase functions:log --project escape-echo --limit 50
firebase functions:log --project escape-echo --only remoteButton --limit 100
```

Or the [Cloud Logging Console](https://console.cloud.google.com/logs/query?project=escape-echo).

### Cloud Functions dashboard

[console.cloud.google.com/functions/list?project=escape-echo](https://console.cloud.google.com/functions/list?project=escape-echo) — per-function request count, error rate, execution time. Fastest way to see if a specific function is misbehaving after a deploy.

### Build history

[console.cloud.google.com/cloud-build/builds?project=escape-echo](https://console.cloud.google.com/cloud-build/builds?project=escape-echo) — one Cloud Build entry per deploy batch. Shows duration, commit SHA, and any build errors.

### Artifact Registry (container images)

[console.cloud.google.com/artifacts/docker/escape-echo/us-central1/gcf-artifacts?project=escape-echo](https://console.cloud.google.com/artifacts/docker/escape-echo/us-central1/gcf-artifacts?project=escape-echo) — one container image per function per deploy. **Note:** the timestamp shown here is the image push time, not the function-live-serving time. For the authoritative "when did the new code start running," use `gcloud functions describe <name> --format='value(updateTime)'`.

### Authoritative deploy timestamp (CLI)

```bash
gcloud functions list --project=escape-echo --regions=us-central1 \
  --format="table(name,versionId,updateTime,runtime)" \
  --sort-by=~updateTime
```

The top row is the most-recently-deployed function. `updateTime` is when Cloud Functions swapped traffic to the new code.

---

## Cost hygiene

### Artifact Registry cleanup policy

Old container images accumulate in `us-central1/gcf-artifacts` every deploy and cost storage. A cleanup policy auto-deletes images older than the retention window.

**Currently configured:** 1 day retention (set 2026-04-21 after a deploy warned about the missing policy).

To verify or reset:

```bash
firebase functions:artifacts:setpolicy --project escape-echo --force
```

### Cloud Functions billing

Free tier covers: 2M invocations/month, 400K GB-seconds memory, 200K GHz-seconds CPU, 5 GB egress. This app is well under — but worth glancing at [Firebase Usage & billing](https://console.firebase.google.com/project/escape-echo/usage) if a deploy introduces new call patterns (e.g., a scheduled job that runs every second by mistake).

### Cloud Build minutes

Free tier: 120 build-minutes/day. Each deploy uses ~3–5 minutes. Sustained daily deploys are safe; automation that loops would blow through it.

---

## Deprecation calendar

Track these to avoid unplanned breakage:

| Item | Deprecation | Action |
|---|---|---|
| Node.js 20 runtime | ~~**2026-04-30 soft / 2026-10-30 hard**~~ | **Done in `server/10`.** `engines.node` and `.nvmrc` now pin Node 22. |
| Node.js 22 runtime | not yet announced | Watch [Cloud Functions runtime support](https://cloud.google.com/functions/docs/runtime-support) for next deprecation; same recipe applies. |
| `firebase-functions` 6.x | Newer major available | Upgrade when a needed API is in 7.x; no urgency otherwise. |
| Cloud Functions 1st Gen | Google is migrating users to 2nd Gen; no firm EOL announced | Migration is a larger change — schedule a dedicated sprint when forced. |

---

## Architecture (deploy pipeline)

```
  git tag push (server/N)
       │
       ▼
  .github/workflows/firebase-deploy.yml
       │
       ├── verify-ci — confirms Firebase CI passed on the tagged commit
       │
       └── deploy
           ├── npm install / build
           ├── install firebase-tools globally
           ├── google-github-actions/auth@v2
           │     credentials_json = ${{ secrets.FIREBASE_SERVICE_ACCOUNT }}
           └── firebase deploy --only functions \
                 --project ${{ secrets.FIREBASE_PROJECT_ID }}
                       │
                       ▼
                GCP project escape-echo
                  ├── Cloud Build — builds container per function
                  ├── Artifact Registry — stores images in gcf-artifacts/<fn>
                  └── Cloud Functions Update API — swaps traffic to new code
```

---

## Required GitHub repository secrets

Set in GitHub → Settings → Secrets and variables → Actions.

| Secret | Value | Used by |
|---|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | Full JSON key file contents for `github-actions-deploy@escape-echo.iam.gserviceaccount.com` | `google-github-actions/auth@v2` → `credentials_json` |
| `FIREBASE_PROJECT_ID` | `escape-echo` | `firebase deploy --project` |
| `FIREBASE_TOKEN` | Legacy CI token (unused by the current deploy workflow) | Historical — safe to ignore |

Other secrets (`SERVER_CONFIG_KEY`, etc.) belong to Android CI, not Firebase deploys.

---

## Required GCP APIs

Firebase CLI checks these during deploy and fails if any are missing. One-time enablement per project; no recurring cost.

- `cloudbilling.googleapis.com` — **blocked every deploy through 2026-04-21** because it was disabled
- `cloudfunctions.googleapis.com`
- `cloudbuild.googleapis.com`
- `artifactregistry.googleapis.com`
- `cloudscheduler.googleapis.com` (needed because several functions are scheduled Pub/Sub triggers)
- `firebaseextensions.googleapis.com` (auto-enabled by Firebase CLI on first deploy)

---

## Deployer service account (the one the CI uses)

`github-actions-deploy@escape-echo.iam.gserviceaccount.com`

### Project-level roles

- `roles/firebase.admin` — broad Firebase surface
- `roles/cloudfunctions.developer` — create/update Cloud Functions
- `roles/artifactregistry.writer` — push container images to `us-central1/gcf-artifacts`
- `roles/cloudscheduler.admin` — update scheduled Pub/Sub jobs

### Per-service-account role (scoped)

`roles/iam.serviceAccountUser` on `escape-echo@appspot.gserviceaccount.com` (the runtime SA the deploy impersonates). **This is the one that's easiest to miss and caused the months-long silent failure** — without it, the Cloud Functions Update API returns a generic "failed to update" error that `firebase-tools` reports as a warning while still exiting 0.

---

## Re-provisioning from scratch

Run these from a machine where `gcloud` is authenticated as a project owner (or edit-IAM role).

```bash
PROJECT=escape-echo
SA_NAME=github-actions-deploy
SA="${SA_NAME}@${PROJECT}.iam.gserviceaccount.com"

# 1. Create the service account (skip if it exists)
gcloud iam service-accounts create "$SA_NAME" \
  --display-name="GitHub Actions Firebase Deploy" \
  --project="$PROJECT"

# 2. Grant project-level roles
for role in \
  roles/firebase.admin \
  roles/cloudfunctions.developer \
  roles/artifactregistry.writer \
  roles/cloudscheduler.admin
do
  gcloud projects add-iam-policy-binding "$PROJECT" \
    --member="serviceAccount:$SA" \
    --role="$role" \
    --condition=None
done

# 3. Grant serviceAccountUser on the runtime SA (the one that's easy to miss)
gcloud iam service-accounts add-iam-policy-binding \
  "${PROJECT}@appspot.gserviceaccount.com" \
  --project="$PROJECT" \
  --member="serviceAccount:$SA" \
  --role="roles/iam.serviceAccountUser"

# 4. Generate a JSON key
KEYFILE=$(mktemp /tmp/gha-key.XXXXXX)
chmod 600 "$KEYFILE"
gcloud iam service-accounts keys create "$KEYFILE" \
  --iam-account="$SA" \
  --project="$PROJECT"

# 5. Upload as the FIREBASE_SERVICE_ACCOUNT secret
gh secret set FIREBASE_SERVICE_ACCOUNT --repo cartland/SmartGarageDoor < "$KEYFILE"

# 6. Immediately delete the local key — it's a valid credential
shred -u "$KEYFILE" 2>/dev/null || rm -f "$KEYFILE"
```

### Rotating just the key (keeping the same SA)

Skip steps 1–3. Run 4–6, then clean up the old key:

```bash
gcloud iam service-accounts keys list \
  --iam-account="$SA" --project="$PROJECT"

gcloud iam service-accounts keys delete <OLD_KEY_ID> \
  --iam-account="$SA" --project="$PROJECT"
```

### Verifying the SA has what it needs

```bash
gcloud projects get-iam-policy escape-echo \
  --flatten="bindings[].members" \
  --filter="bindings.members:github-actions-deploy@escape-echo.iam.gserviceaccount.com" \
  --format="value(bindings.role)"
```

Should print 4 roles (`firebase.admin`, `cloudfunctions.developer`, `artifactregistry.writer`, `cloudscheduler.admin`). The `serviceAccountUser` role is on a different SA and won't appear here — check it separately:

```bash
gcloud iam service-accounts get-iam-policy \
  escape-echo@appspot.gserviceaccount.com --project=escape-echo
```

### Retrying a failed deploy after fixing roles

```bash
gh run list --workflow firebase-deploy.yml --limit 3
gh run rerun <run-id>
```

---

## Troubleshooting

| Symptom | Most likely cause | Fix |
|---|---|---|
| `Error: Cloud Billing API has not been used in project ...` | Cloud Billing API disabled | Enable at [console.developers.google.com/apis/api/cloudbilling.googleapis.com](https://console.developers.google.com/apis/api/cloudbilling.googleapis.com/overview?project=587667443259). No cost. |
| `⚠ functions: failed to update function <name>` (no further detail, exit 0) | Deployer SA missing `roles/iam.serviceAccountUser` on the runtime SA | See "Deployer service account" above. Step 3 of re-provisioning. |
| `Error: HTTP Error: 403, Permission iam.serviceAccounts.actAs denied` | Same as above, clearer error wording | Same fix. |
| `Error: Failed to list Firebase projects. See firebase-debug.log` | Stale OAuth token on a user account | `firebase login --reauth` in a **real terminal** with a browser. Does not apply to CI — CI uses the SA key, not OAuth. |
| `Error: Cannot run login in non-interactive mode` | Trying to `firebase login` from a non-TTY shell | Must be run from a real terminal. Alternative: generate a long-lived token with `firebase login:ci` (still needs a browser for the initial auth). |
| `Error: firebase-tools no longer supports Java version before 21` | Firebase CLI 14.x requires JDK 21+ for the emulators | Install JDK 21. In CI, add `actions/setup-java@v4` with `java-version: '21'` (already done in `firebase-ci-checks.yml`). |
| Deploy succeeds but `Error: Functions successfully deployed but could not set up cleanup policy` | Artifact Registry needs a cleanup policy | `firebase functions:artifacts:setpolicy --project escape-echo --force`. One-time. |
| Node 22.18+ / Node 24 local: `TypeError: admin.initializeApp is not a function` (or `Cannot read properties of undefined ...` on `admin.apps`) | Node 22.18+ enables native TS strip-types by default, which breaks `import * as admin from 'firebase-admin'` under mocha's `ts-node/register`. | The `tests` npm script pins `NODE_OPTIONS='--no-experimental-strip-types'` so ts-node owns compilation. CI uses `scripts/firebase-npm.sh` which auto-applies this. If you bypass that wrapper, set the env var by hand. |

---

## Related files

- `.github/workflows/firebase-deploy.yml` — the deploy workflow itself
- `.github/workflows/firebase-ci.yml` + `firebase-ci-checks.yml` — the CI gate that must pass before a tag deploys (build, lint, tests, npm audit warning, emulator smoke, FCM contract tests)
- `scripts/release-firebase.sh` — cuts `server/N` tags
- `FirebaseServer/firebase.json` — functions config consumed by `firebase deploy`
- `FirebaseServer/package.json` — runtime pinned to Node 22; `overrides` block applies transitive dep fixes (e.g., `jws` security override)
- `FirebaseServer/test/controller/VerifyIdTokenTest.ts` — library-chain regression guard for the JWT verification path

---

## History reference

- **2026-04-21** — github-actions-deploy SA created; 5 IAM roles granted; `FIREBASE_SERVICE_ACCOUNT` rotated; `server/4` and `server/5` both CI-deployed successfully. Cloud Billing API enabled on the project. Artifact Registry cleanup policy set (1-day retention).
- **Pre-2026-04-21** — every CI deploy attempt silently failed. All production deploys were manual via `firebase deploy` from a dev machine.
