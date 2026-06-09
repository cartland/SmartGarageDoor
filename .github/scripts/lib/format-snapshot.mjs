// Pure rendering for the Play Store track-state snapshot. No I/O, no API, no
// git — everything it needs is passed in, so it is unit-testable in isolation.
//
// `tracks`            : array of androidpublisher Track objects
//                       ({ track, releases: [{ status, versionCodes, userFraction, name }] })
// `resolveVersionName`: (versionCode: string) => string  — maps a code to its
//                       versionName (production uses git tags; tests pass a stub)
// `timestamp`,`trigger`,`packageName`: header/footer metadata
//
// Returns { comment, body, machine }:
//   comment  Markdown for the appended history comment
//   body     Markdown for the issue description (comment + a "current state" preamble)
//   machine  the structured object embedded as raw JSON (also handy for tests)

// Tracks render in this order; any custom/closed-testing tracks append after.
export const DISPLAY_ORDER = ['internal', 'alpha', 'beta', 'production']

function rollout(release) {
  if (release.userFraction != null) return `${Math.round(release.userFraction * 100)}%`
  return release.status === 'completed' ? '100%' : '—'
}

export function renderSnapshot({ tracks, resolveVersionName, timestamp, trigger, packageName }) {
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
      const names = vcs.map(resolveVersionName)
      const status = r.status || '—'
      rows.push(
        `| ${name} | ${vcs.join(', ') || '—'} | ${names.join(', ') || '—'} | ${status} | ${rollout(r)} |`,
      )
      machine.tracks[name] = {
        versionCodes: vcs.map(Number),
        versionNames: names,
        status,
        userFraction: r.userFraction ?? null,
        releaseName: r.name ?? null,
      }
    }
  }

  const comment = [
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

  const body = [
    `**Current Play Store track state** — this description is overwritten on each run.`,
    `The full append-only history is in the comments below.`,
    ``,
    comment,
  ].join('\n')

  return { comment, body, machine }
}
