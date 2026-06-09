// Reads the current Play Store release-track state (read-only) and writes two
// Markdown files for the track-state log issue:
//   snapshot-comment.md  appended as a new comment (immutable history)
//   snapshot-body.md     written to the issue description (latest state)
// Never publishes or mutates the store — it inserts an edit, lists tracks, and
// abandons the edit.
//
// Inputs (env):
//   GOOGLE_PLAY_SERVICE_ACCOUNT_JSON  Service account JSON (same secret used to upload)
//   PACKAGE_NAME                      App package, e.g. com.chriscartland.garage
//   SNAPSHOT_TRIGGER                  Human label for what triggered this run
//   SNAPSHOT_TIMESTAMP                Pre-formatted UTC timestamp for the header
//
// Rendering lives in lib/format-snapshot.mjs (pure, unit-tested). This entry
// point only does auth + the API read + the git-tag versionName lookup.
//
// versionCode -> versionName mapping: each release is tagged `android/N` where
// N == versionCode, so `git show android/N:AndroidGarage/version.properties`
// yields the versionName for that build. Requires a full-history checkout
// (fetch-depth: 0) so the tags are present.

import pkg from 'googleapis'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { renderSnapshot } from './lib/format-snapshot.mjs'

const { google } = pkg

const packageName = process.env.PACKAGE_NAME || 'com.chriscartland.garage'
const saJson = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
const trigger = process.env.SNAPSHOT_TRIGGER || 'manual'
const timestamp = process.env.SNAPSHOT_TIMESTAMP || new Date().toISOString()

if (!saJson) {
  console.error('Missing GOOGLE_PLAY_SERVICE_ACCOUNT_JSON')
  process.exit(1)
}

const versionNameCache = new Map()
function resolveVersionName(vc) {
  if (versionNameCache.has(vc)) return versionNameCache.get(vc)
  let name
  try {
    const out = execFileSync('git', ['show', `android/${vc}:AndroidGarage/version.properties`], {
      encoding: 'utf8',
    })
    const m = out.match(/versionName=(.+)/)
    name = m ? m[1].trim() : '(unknown)'
  } catch {
    name = '(no tag)'
  }
  versionNameCache.set(vc, name)
  return name
}

const auth = new google.auth.GoogleAuth({
  credentials: JSON.parse(saJson),
  scopes: ['https://www.googleapis.com/auth/androidpublisher'],
})
const publisher = google.androidpublisher({ version: 'v3', auth })

const { data: edit } = await publisher.edits.insert({ packageName })
const editId = edit.id

let tracks = []
try {
  const res = await publisher.edits.tracks.list({ packageName, editId })
  tracks = res.data.tracks || []
} finally {
  // Read-only: discard the edit so nothing is committed to the store.
  try {
    await publisher.edits.delete({ packageName, editId })
  } catch {
    // best effort; abandoned edits expire on their own
  }
}

const { comment, body } = renderSnapshot({
  tracks,
  resolveVersionName,
  timestamp,
  trigger,
  packageName,
})

writeFileSync('snapshot-comment.md', comment)
writeFileSync('snapshot-body.md', body)
console.log(comment)
