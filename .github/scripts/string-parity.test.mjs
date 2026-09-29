// Cross-platform string parity (docs/CROSS_SURFACE_UX_STRATEGY.md § 4.1).
//
// Two halves. The unit tests pin the parsers and prove the checker can fail
// (positive controls: a mismatch, a missing key, an entry naming too few
// platforms). The last test is the real gate: it loads the three string
// sources and the curated map from the repo and asserts parity holds.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { checkParity, parseAndroidStrings, parseStringCatalog } from './lib/string-parity.mjs'

const REPO = new URL('../../', import.meta.url)
const read = (path) => readFileSync(new URL(path, REPO), 'utf8')

const ANDROID_XML = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="home_door_state_open">Open</string>
    <string name="settings_snooze_option_none">Don\\'t snooze</string>
    <string name="multi_line" translatable="false">First
        second</string>
</resources>
`

const CATALOG_JSON = JSON.stringify({
  sourceLanguage: 'en',
  strings: { Open: {}, "Don't snooze": {}, 'No signal': {} },
  version: '1.0',
})

test('parses Android strings, unescaping the apostrophe and folding whitespace', () => {
  const strings = parseAndroidStrings(ANDROID_XML)
  assert.equal(strings.get('home_door_state_open'), 'Open')
  assert.equal(strings.get('settings_snooze_option_none'), "Don't snooze")
  assert.equal(strings.get('multi_line'), 'First second')
  assert.equal(strings.size, 3)
})

test('parses a String Catalog into its keys', () => {
  const keys = parseStringCatalog(CATALOG_JSON)
  assert.deepEqual([...keys].sort(), ["Don't snooze", 'No signal', 'Open'])
  assert.throws(() => parseStringCatalog('{"nope": 1}'), /not a String Catalog/)
})

const sources = {
  android: parseAndroidStrings(ANDROID_XML),
  wear: new Map([['door_state_open', 'Open']]),
  ios: parseStringCatalog(CATALOG_JSON),
}

test('parity holds when every named platform has the same word', () => {
  const entries = [
    { _comment: 'ignored' },
    { concept: 'DoorHeadline.OPEN', android: 'home_door_state_open', wear: 'door_state_open', ios: 'Open' },
    { concept: 'SnoozeDurationUIOption.None', android: 'settings_snooze_option_none', ios: "Don't snooze" },
  ]
  assert.deepEqual(checkParity(entries, sources), [])
})

test('a differing word is a problem (positive control)', () => {
  const entries = [{ concept: 'DoorHeadline.OPEN', android: 'home_door_state_open', wear: 'door_state_open', ios: 'No signal' }]
  const problems = checkParity(entries, sources)
  assert.equal(problems.length, 1)
  assert.match(problems[0], /DoorHeadline\.OPEN: the platforms disagree/)
  assert.match(problems[0], /ios="No signal"/)
})

test('a missing key is a problem, on any platform', () => {
  const problems = checkParity(
    [
      { concept: 'a', android: 'no_such_string', ios: 'Open' },
      { concept: 'b', android: 'home_door_state_open', ios: 'Not in the catalog' },
      { concept: 'c', android: 'home_door_state_open', wear: 'nope' },
    ],
    sources,
  )
  assert.deepEqual(problems, [
    'a: android has no string "no_such_string"',
    'b: ios has no string "Not in the catalog"',
    'c: wear has no string "nope"',
  ])
})

test('an entry naming fewer than two platforms checks nothing and says so', () => {
  const problems = checkParity([{ concept: 'lonely', android: 'home_door_state_open' }], sources)
  assert.deepEqual(problems, ['lonely: names fewer than two platforms, so it checks nothing'])
})

test('an empty map is itself a problem', () => {
  assert.deepEqual(checkParity([], sources), ['the parity map is empty — it checks nothing'])
})

test('the repo: every curated shared-decision word agrees across Android, Wear and iOS', () => {
  const entries = JSON.parse(read('MobileGarage/string-parity.json'))
  const repoSources = {
    android: parseAndroidStrings(read('MobileGarage/androidApp/src/main/res/values/strings.xml')),
    wear: parseAndroidStrings(read('MobileGarage/wearApp/src/main/res/values/strings.xml')),
    ios: parseStringCatalog(read('MobileGarage/iosApp/GarageControl/Localizable.xcstrings')),
  }
  // Scope sanity: the parsers saw real files, not empty ones.
  assert.ok(repoSources.android.size > 100, `android strings parsed: ${repoSources.android.size}`)
  assert.ok(repoSources.wear.size > 50, `wear strings parsed: ${repoSources.wear.size}`)
  assert.ok(repoSources.ios.size > 100, `ios keys parsed: ${repoSources.ios.size}`)
  const checked = entries.filter((e) => e.concept).length
  assert.ok(checked >= 15, `curated entries: ${checked}`)
  assert.deepEqual(checkParity(entries, repoSources), [])
})
