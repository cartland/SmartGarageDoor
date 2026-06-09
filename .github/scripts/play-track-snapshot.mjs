// Reads the current Play Store release-track state (read-only) and writes a
// Markdown snapshot to `snapshot-comment.md` for appending to the track-state
// log issue. Never publishes or mutates the store — it inserts an edit, lists
// tracks, and abandons the edit.
//
// Inputs (env):
//   GOOGLE_PLAY_SERVICE_ACCOUNT_JSON  Service account JSON (same secret used to upload)
//   PACKAGE_NAME                      App package, e.g. com.chriscartland.garage
//   SNAPSHOT_TRIGGER                  Human label for what triggered this run
//   SNAPSHOT_TIMESTAMP                Pre-formatted UTC timestamp for the header
//
// Output: writes ./snapshot-comment.md and echoes it to stdout.
//
// versionCode -> versionName mapping: each release is tagged `android/N` where
// N == versionCode, so `git show android/N:AndroidGarage/version.properties`
// yields the versionName for that build. Requires a full-history checkout
// (fetch-depth: 0) so the tags are present.

import pkg from 'googleapis'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const { google } = pkg

const packageName = process.env.PACKAGE_NAME || 'com.chriscartland.garage'
const saJson = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
const trigger = process.env.SNAPSHOT_TRIGGER || 'manual'
const timestamp = process.env.SNAPSHOT_TIMESTAMP || new Date().toISOString()

if (!saJson) {
  console.error('Missing GOOGLE_PLAY_SERVICE_ACCOUNT_JSON')
  process.exit(1)
}

// Tracks render in this order; any unknown/custom closed-testing tracks append after.
const DISPLAY_ORDER = ['internal', 'alpha', 'beta', 'production']

const versionNameCache = new Map()
function versionNameForCode(vc) {
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

const byName = new Map(tracks.map((t) => [t.track, t]))
const orderedNames = [
  ...DISPLAY_ORDER.filter((n) => byName.has(n)),
  ...tracks.map((t) => t.track).filter((n) => !DISPLAY_ORDER.includes(n)),
]

const rows = []
const machine = { timestamp, trigger, packageName, tracks: {} }

for (const name of orderedNames) {
  const releases = byName.get(name)?.releases || []
  if (releases.length === 0) {
    rows.push(`| ${name} | — | — | — | — |`)
    machine.tracks[name] = null
    continue
  }
  for (const r of releases) {
    const vcs = (r.versionCodes || []).map(String)
    const names = vcs.map(versionNameForCode)
    const status = r.status || '—'
    const rollout =
      r.userFraction != null
        ? `${Math.round(r.userFraction * 100)}%`
        : status === 'completed'
          ? '100%'
          : '—'
    rows.push(`| ${name} | ${vcs.join(', ') || '—'} | ${names.join(', ') || '—'} | ${status} | ${rollout} |`)
    machine.tracks[name] = {
      versionCodes: vcs.map(Number),
      versionNames: names,
      status,
      userFraction: r.userFraction ?? null,
      releaseName: r.name ?? null,
    }
  }
}

const body = [
  `### Play Store track state — ${timestamp}`,
  ``,
  `**Trigger:** ${trigger}`,
  ``,
  `| Track | versionCode | versionName | Status | Rollout |`,
  `|-------|-------------|-------------|--------|---------|`,
  ...rows,
  ``,
  `<details><summary>Raw snapshot (JSON)</summary>`,
  ``,
  '```json',
  JSON.stringify(machine, null, 2),
  '```',
  ``,
  `</details>`,
  ``,
  `<sub>Read-only via androidpublisher \`edits.tracks.list\` · package \`${packageName}\`</sub>`,
].join('\n')

writeFileSync('snapshot-comment.md', body)
console.log(body)
