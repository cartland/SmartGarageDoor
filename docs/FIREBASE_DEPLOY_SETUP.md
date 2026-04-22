# Firebase CI Deploy Setup

How the Firebase Cloud Functions deployment works from GitHub Actions, and how to recreate the setup if anything gets lost or rotated.

**No secrets in this document.** Only the structure and the commands to re-provision. Actual credentials live in GitHub Actions secrets and are never checked into git.

## Status

As of **2026-04-21**:

- ✅ **Manual deploy (local)** — verified. `firebase deploy --only functions` works from a developer machine logged in as the project owner.
- ✅ **Production currently on `server/4`** — jws security override applied (Dependabot alerts #25, #26 closed).
- ✅ **CI-automated deploy (GitHub Actions)** — verified working end-to-end as of 2026-04-21 after the service-account re-provisioning described below. Run: Actions tab → `Deploy Firebase Server` → run against `server/4` → 17 functions updated, "Deploy complete!"
- ⚠️ **Previous CI deploys never worked** — every prior run of `firebase-deploy.yml` failed at "failed to update function echo" because the old service account behind `FIREBASE_SERVICE_ACCOUNT` lacked the roles needed to call the Cloud Functions Update API. All pre-2026-04-21 production deploys were manual. This was fixed by creating a new dedicated deployer service account (`github-actions-deploy@escape-echo.iam.gserviceaccount.com`) with a minimum-necessary role set and rotating the `FIREBASE_SERVICE_ACCOUNT` secret to use its key.

## Architecture at a glance

```
  tag push (server/N)
       │
       ▼
  .github/workflows/firebase-deploy.yml
       │
       ├── verify-ci: confirms Firebase CI passed on the tagged commit
       │
       └── deploy:
           ├── npm install / build
           ├── install firebase-tools globally
           ├── google-github-actions/auth@v2
           │     credentials_json = ${{ secrets.FIREBASE_SERVICE_ACCOUNT }}
           └── firebase deploy --only functions \
                 --project ${{ secrets.FIREBASE_PROJECT_ID }}
                       │
                       ▼
                GCP project `escape-echo`
```

## GCP project

- **Project ID:** `escape-echo`
- **Project number:** `587667443259`
- **Region for functions:** `us-central1`
- **Runtime service account (impersonated by deploys):** `escape-echo@appspot.gserviceaccount.com` — the default App Engine service account, baked into the project.

## Required GitHub repository secrets

Set in GitHub → Settings → Secrets and variables → Actions.

| Secret | Value | Where it's used |
|---|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | Full JSON key file contents for the deployer service account (see below) | Pasted into `google-github-actions/auth@v2` as `credentials_json` |
| `FIREBASE_PROJECT_ID` | `escape-echo` | Passed as `--project` to `firebase deploy` |
| `FIREBASE_TOKEN` | Legacy CI token from `firebase login:ci` | Historical; not used by the current deploy workflow but still present in the repo secret list |

## GCP APIs that must be enabled

Firebase CLI checks these during deploy and fails if any are missing. Enable once per project (one-time setup; no recurring cost for being enabled).

- `cloudbilling.googleapis.com` — **Cloud Billing API** (this one blocked every deploy through 2026-04-21; was disabled until the project owner enabled it)
- `cloudfunctions.googleapis.com`
- `cloudbuild.googleapis.com`
- `artifactregistry.googleapis.com`
- `cloudscheduler.googleapis.com` — needed only if any function is a scheduled Pub/Sub trigger; this repo has several
- `firebaseextensions.googleapis.com` — enabled automatically by Firebase CLI on first deploy

## Deployer service account

Created and managed via `gcloud`. **Do not** give this account owner-level or project-admin rights — the role list below is the minimum needed for functions deploys.

- **Email:** `github-actions-deploy@escape-echo.iam.gserviceaccount.com`
- **Project-level roles:**
  - `roles/firebase.admin` — broad Firebase surface (needed for the Firebase CLI to talk to Firebase-specific APIs)
  - `roles/cloudfunctions.developer` — create/update Cloud Functions
  - `roles/artifactregistry.writer` — push container images to `us-central1/gcf-artifacts`
  - `roles/cloudscheduler.admin` — update scheduled Pub/Sub jobs
- **Per-service-account role** (scoped to the runtime SA it impersonates):
  - `roles/iam.serviceAccountUser` on `escape-echo@appspot.gserviceaccount.com`

## Re-provisioning from scratch

If the service account key is rotated, revoked, or lost — or if you want to migrate to a fresh deployer SA — run these commands from a machine where `gcloud` is authenticated as a project owner.

### 1. Create the service account (skip if it already exists)

```bash
PROJECT=escape-echo
SA_NAME=github-actions-deploy
SA="${SA_NAME}@${PROJECT}.iam.gserviceaccount.com"

gcloud iam service-accounts create "$SA_NAME" \
  --display-name="GitHub Actions Firebase Deploy" \
  --project="$PROJECT"
```

### 2. Grant project-level roles

```bash
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
```

### 3. Grant `serviceAccountUser` on the runtime SA

Without this, the Cloud Functions Update API rejects the deploy with "failed to update function" — the symptom we hit before.

```bash
gcloud iam service-accounts add-iam-policy-binding \
  "${PROJECT}@appspot.gserviceaccount.com" \
  --project="$PROJECT" \
  --member="serviceAccount:$SA" \
  --role="roles/iam.serviceAccountUser"
```

### 4. Generate a JSON key

```bash
KEYFILE=$(mktemp /tmp/gha-key.XXXXXX)
chmod 600 "$KEYFILE"
gcloud iam service-accounts keys create "$KEYFILE" \
  --iam-account="$SA" \
  --project="$PROJECT"
```

### 5. Upload as the `FIREBASE_SERVICE_ACCOUNT` secret

```bash
gh secret set FIREBASE_SERVICE_ACCOUNT --repo cartland/SmartGarageDoor < "$KEYFILE"
```

### 6. Delete the local key immediately

A JSON key is a valid credential — keep it off disk.

```bash
shred -u "$KEYFILE" 2>/dev/null || rm -f "$KEYFILE"
```

### 7. (Optional) Clean up old keys

List all active keys for the SA and delete anything older:

```bash
gcloud iam service-accounts keys list \
  --iam-account="$SA" --project="$PROJECT"

gcloud iam service-accounts keys delete <OLD_KEY_ID> \
  --iam-account="$SA" --project="$PROJECT"
```

### 8. Retry the failing workflow

If there's a recent failed run, re-run it — no need to re-push the tag:

```bash
gh run list --workflow firebase-deploy.yml --limit 3
gh run rerun <run-id>
```

Otherwise the next release (`server/N+1` tag push) will exercise the new credentials automatically.

## Verifying a deploy

The deploy succeeds when the log shows all functions with a ✔ and ends with `Deploy complete!`:

```
✔ functions[echo(us-central1)]                    Successful update operation.
✔ functions[remoteButton(us-central1)]            Successful update operation.
✔ functions[serverConfig(us-central1)]            Successful update operation.
... (17 total)
✔ Deploy complete!
```

**Beware of fake-success.** Before 2026-04-21, `firebase deploy` was exiting 0 despite printing `⚠ functions: failed to update function ...` — the workflow "succeeded" but nothing deployed. If you see that warning pattern, the service account is missing a role. The smoke test (described below) will not catch this class of failure because the existing production endpoints remain reachable from the old deploy.

### Quick smoke check after deploy

Hit any unauthenticated endpoint:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  https://us-central1-escape-echo.cloudfunctions.net/serverConfig
```

Expected: `401` (handler reached, returns Unauthorized because no `X-ServerConfigKey` header was sent). Any 5xx or a connection-refused means something's wrong.

## Troubleshooting

| Symptom | Most likely cause |
|---|---|
| `Error: Cloud Billing API has not been used in project ...` | Cloud Billing API disabled. Enable at [console.developers.google.com/apis/api/cloudbilling.googleapis.com](https://console.developers.google.com/apis/api/cloudbilling.googleapis.com/overview?project=587667443259). |
| `⚠ functions: failed to update function <name>` (no further detail, exit 0) | Deployer SA missing `roles/iam.serviceAccountUser` on the runtime SA. Step 3 above. |
| `Error: HTTP Error: 403, Permission iam.serviceAccounts.actAs denied` | Same as above, but with the clearer error. |
| `Error: Failed to list Firebase projects. See firebase-debug.log` | Stale OAuth token for a user account. Run `firebase login --reauth` in a terminal with a browser. Not applicable to CI (uses SA key, not OAuth). |
| Deploy succeeds but `Error: Functions successfully deployed but could not set up cleanup policy` | Artifact Registry needs a cleanup policy. One-time fix: `firebase functions:artifacts:setpolicy --project escape-echo --force` |

## Related files

- `.github/workflows/firebase-deploy.yml` — the deploy workflow itself
- `.github/workflows/firebase-ci-checks.yml` — the CI checks (build, tests, audit, emulator smoke) that must pass before a tag deploys
- `scripts/release-firebase.sh` — the script used to cut a new `server/N` tag
- `FirebaseServer/firebase.json` — the functions config consumed by `firebase deploy`
