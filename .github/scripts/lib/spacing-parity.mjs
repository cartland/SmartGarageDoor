// Cross-platform spacing-token parity (docs/CROSS_SURFACE_UX_STRATEGY.md § 4.5).
//
// The phone's `ui/theme/Spacing.kt`, iOS's `Core/Theme/GarageSpacing.swift`
// and the watch's `WearSpacing.kt` each declare spacing tokens by ROLE, and
// the iOS file says it "mirrors" the Android one — but nothing compared the
// numbers, so a token could drift on one platform with no test noticing
// (§ 3.8: "spacing tokens … are all unpinned"). This module is pure (no I/O)
// so the parsers and the checker can be unit-tested with positive controls;
// the test supplies file text and the curated map.
//
// The map lives in MobileGarage/spacing-parity.json. Each entry names one
// role and, per platform, the token that carries it:
//   - android / wear: `Object.Token` in a Kotlin file (`val Token = N.dp`, or
//     `val Token = PaddingValues(N.dp)` for a uniform card inset)
//   - ios: the `static let name: CGFloat = N` in GarageSpacing.swift
// A platform without the role is left out; an entry must still name two.

/**
 * Parse Kotlin spacing tokens into a Map of `Object.Token` → dp number.
 * Tracks the enclosing `object X {` so tokens are addressed by object; an
 * object ends at a line that is exactly `}`.
 */
export function parseKotlinDpTokens(source) {
  const out = new Map()
  let object = null
  const objectLine = /^object\s+([A-Za-z_][A-Za-z0-9_]*)\s*\{/
  const token = /^\s*val\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?:PaddingValues\(\s*)?(\d+(?:\.\d+)?)\.dp\s*\)?\s*$/
  for (const line of source.split('\n')) {
    const open = objectLine.exec(line)
    if (open) {
      object = open[1]
      continue
    }
    if (line === '}') {
      object = null
      continue
    }
    const match = token.exec(line)
    if (match && object) {
      out.set(`${object}.${match[1]}`, Number(match[2]))
    }
  }
  return out
}

/** Parse Swift `static let name: CGFloat = N` tokens into a Map of name → number. */
export function parseSwiftCGFloatTokens(source) {
  const out = new Map()
  const token = /^\s*static\s+let\s+([A-Za-z_][A-Za-z0-9_]*)\s*:\s*CGFloat\s*=\s*(\d+(?:\.\d+)?)\s*$/
  for (const line of source.split('\n')) {
    const match = token.exec(line)
    if (match) out.set(match[1], Number(match[2]))
  }
  return out
}

const PLATFORMS = ['android', 'wear', 'ios']

/**
 * Check every entry of the curated map against the parsed sources.
 *
 * @param entries the parsed MobileGarage/spacing-parity.json
 * @param sources { android: Map, wear: Map, ios: Map } of token → number
 * @returns human-readable problems; empty means parity holds
 */
export function checkSpacingParity(entries, sources) {
  const problems = []
  if (!Array.isArray(entries) || entries.length === 0) {
    return ['the spacing parity map is empty — it checks nothing']
  }
  for (const entry of entries) {
    if (isCommentOnly(entry)) continue
    if (!entry.role) {
      problems.push(`an entry has no "role": ${JSON.stringify(entry)}`)
      continue
    }
    const named = PLATFORMS.filter((platform) => entry[platform] !== undefined && entry[platform] !== null)
    if (named.length < 2) {
      problems.push(`${entry.role}: names fewer than two platforms, so it checks nothing`)
      continue
    }
    const values = []
    let missing = false
    for (const platform of named) {
      const ref = entry[platform]
      const value = sources[platform].get(ref)
      if (value === undefined) {
        problems.push(`${entry.role}: ${platform} has no token "${ref}"`)
        missing = true
        continue
      }
      values.push({ platform, ref, value })
    }
    if (missing) continue
    const distinct = new Set(values.map((v) => v.value))
    if (distinct.size > 1) {
      const detail = values.map((v) => `${v.platform} ${v.ref}=${v.value}`).join(', ')
      problems.push(`${entry.role}: the platforms disagree — ${detail}`)
    }
  }
  return problems
}

function isCommentOnly(entry) {
  const keys = Object.keys(entry)
  return keys.length === 1 && keys[0] === '_comment'
}
