// Cross-platform string parity (docs/CROSS_SURFACE_UX_STRATEGY.md § 4.1).
//
// ADR-035: shared code decides which variant the user sees; each platform
// words it. Nothing had ever loaded the three string sources together, so
// the words that are SUPPOSED to be the same — the door headline, the two
// no-door headlines, the snooze sheet — could drift on one platform with no
// test noticing. This module is pure (no I/O; the test supplies file text)
// so it can be unit-tested, including the positive controls.
//
// The curated map lives in MobileGarage/string-parity.json. Each entry names
// one shared decision and, per platform, where its words live:
//   - android / wear: a <string name="..."> in that app's strings.xml
//   - ios: the key in Localizable.xcstrings, which IS the English text
// A platform may be absent (null / omitted) when the surface does not exist
// there yet; an entry must still name at least two, or it checks nothing.

/**
 * Parse an Android `strings.xml` into a Map of name → English value.
 * Handles single- and multi-line values and the resource escapes that
 * matter for parity (`\'`, `\"`, `\n`), and leaves the rest verbatim.
 */
export function parseAndroidStrings(xml) {
  const out = new Map()
  const pattern = /<string\s+[^>]*name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g
  let match
  while ((match = pattern.exec(xml)) !== null) {
    out.set(match[1], unescapeAndroid(match[2]))
  }
  return out
}

function unescapeAndroid(value) {
  return value
    .replace(/\\'/g, "'")
    .replace(/\\"/g, '"')
    .replace(/\\n/g, '\n')
    .replace(/\s+/g, ' ')
    .trim()
}

/** Parse a String Catalog (`Localizable.xcstrings`) into the Set of its keys. */
export function parseStringCatalog(json) {
  const catalog = JSON.parse(json)
  if (!catalog || typeof catalog.strings !== 'object') {
    throw new Error('not a String Catalog: no "strings" object')
  }
  return new Set(Object.keys(catalog.strings))
}

const PLATFORMS = ['android', 'wear', 'ios']

/**
 * Check every entry of the curated map against the loaded sources.
 *
 * @param entries the parsed MobileGarage/string-parity.json
 * @param sources { android: Map, wear: Map, ios: Set }
 * @returns a list of human-readable problems; empty means parity holds
 */
export function checkParity(entries, sources) {
  const problems = []
  if (!Array.isArray(entries) || entries.length === 0) {
    return ['the parity map is empty — it checks nothing']
  }
  for (const entry of entries) {
    if (isCommentOnly(entry)) continue
    if (!entry.concept) {
      problems.push(`an entry has no "concept": ${JSON.stringify(entry)}`)
      continue
    }
    const named = PLATFORMS.filter((platform) => entry[platform] !== undefined && entry[platform] !== null)
    if (named.length < 2) {
      problems.push(`${entry.concept}: names fewer than two platforms, so it checks nothing`)
      continue
    }
    const words = []
    let missing = false
    for (const platform of named) {
      const ref = entry[platform]
      const word = lookup(platform, ref, sources)
      if (word === undefined) {
        problems.push(`${entry.concept}: ${platform} has no string "${ref}"`)
        missing = true
        continue
      }
      words.push({ platform, word })
    }
    // A missing key is already the problem; comparing what is left would
    // only report the same defect a second way.
    if (missing) continue
    const distinct = new Set(words.map((w) => w.word))
    if (distinct.size > 1) {
      const detail = words.map((w) => `${w.platform}="${w.word}"`).join(', ')
      problems.push(`${entry.concept}: the platforms disagree — ${detail}`)
    }
  }
  return problems
}

/** JSON has no comments; the map's first element carries one as `_comment` and nothing else. */
function isCommentOnly(entry) {
  const keys = Object.keys(entry)
  return keys.length === 1 && keys[0] === '_comment'
}

function lookup(platform, ref, sources) {
  if (platform === 'ios') {
    // The catalog key is the English text itself; presence is the check.
    return sources.ios.has(ref) ? ref : undefined
  }
  return sources[platform].get(ref)
}
