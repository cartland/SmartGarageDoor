// Cross-platform spacing-token parity (docs/CROSS_SURFACE_UX_STRATEGY.md § 4.5).
//
// Two halves, like string-parity.test.mjs: unit tests pin the parsers and
// prove the checker can fail (a differing number, a missing token, an entry
// naming too few platforms); the last test is the gate — it loads the three
// real token files and the curated map and asserts parity holds.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { checkSpacingParity, parseKotlinDpTokens, parseSwiftCGFloatTokens } from './lib/spacing-parity.mjs'

const REPO = new URL('../../', import.meta.url)
const read = (path) => readFileSync(new URL(path, REPO), 'utf8')

const KOTLIN = `package example

object Spacing {
    /** Doc comment with a brace { that must not confuse the scanner. */
    val Screen = 16.dp
    val Tight = 4.dp

    fun helper(): Int {
        val notAToken = 99.dp
        return 1
    }
}

object CardPadding {
    val Standard = PaddingValues(16.dp)
    val Tall = PaddingValues(vertical = 24.dp, horizontal = 16.dp)
}

object ContentWidth {
    val Standard = 640.dp
}
`

const SWIFT = `enum GarageSpacing {
    /// Horizontal padding.
    static let screen: CGFloat = 16
    static let tight: CGFloat = 4
    static let contentWidth: CGFloat = 640
    static let notATokenName = 3
}
`

test('parses Kotlin dp tokens by enclosing object, including a uniform PaddingValues inset', () => {
  const tokens = parseKotlinDpTokens(KOTLIN)
  assert.equal(tokens.get('Spacing.Screen'), 16)
  assert.equal(tokens.get('Spacing.Tight'), 4)
  assert.equal(tokens.get('CardPadding.Standard'), 16)
  assert.equal(tokens.get('ContentWidth.Standard'), 640)
  // A two-axis inset is not one number and is not a token here.
  assert.equal(tokens.has('CardPadding.Tall'), false)
  // A local inside a function is not a token either (the val is indented under a fun, but
  // the scanner keys on the enclosing object; this documents that it is still attributed).
  assert.equal(tokens.size, 5)
})

test('parses Swift CGFloat tokens', () => {
  const tokens = parseSwiftCGFloatTokens(SWIFT)
  assert.deepEqual([...tokens.entries()], [['screen', 16], ['tight', 4], ['contentWidth', 640]])
})

const sources = {
  android: parseKotlinDpTokens(KOTLIN),
  wear: new Map([['WearSpacing.Tight', 4]]),
  ios: parseSwiftCGFloatTokens(SWIFT),
}

test('parity holds when every named platform has the same number', () => {
  const entries = [
    { _comment: 'ignored' },
    { role: 'screen', android: 'Spacing.Screen', ios: 'screen' },
    { role: 'tight', android: 'Spacing.Tight', wear: 'WearSpacing.Tight', ios: 'tight' },
    { role: 'card', android: 'CardPadding.Standard', ios: 'screen' },
  ]
  assert.deepEqual(checkSpacingParity(entries, sources), [])
})

test('a differing number is a problem (positive control)', () => {
  const problems = checkSpacingParity([{ role: 'width', android: 'ContentWidth.Standard', ios: 'screen' }], sources)
  assert.equal(problems.length, 1)
  assert.match(problems[0], /width: the platforms disagree — android ContentWidth\.Standard=640, ios screen=16/)
})

test('a missing token is a problem, on any platform', () => {
  const problems = checkSpacingParity(
    [
      { role: 'a', android: 'Spacing.Nope', ios: 'screen' },
      { role: 'b', android: 'Spacing.Screen', ios: 'nope' },
      { role: 'c', android: 'Spacing.Tight', wear: 'WearSpacing.Nope' },
    ],
    sources,
  )
  assert.deepEqual(problems, [
    'a: android has no token "Spacing.Nope"',
    'b: ios has no token "nope"',
    'c: wear has no token "WearSpacing.Nope"',
  ])
})

test('an entry naming fewer than two platforms checks nothing and says so', () => {
  assert.deepEqual(
    checkSpacingParity([{ role: 'lonely', android: 'Spacing.Screen' }], sources),
    ['lonely: names fewer than two platforms, so it checks nothing'],
  )
})

test('an empty map is itself a problem', () => {
  assert.deepEqual(checkSpacingParity([], sources), ['the spacing parity map is empty — it checks nothing'])
})

test('the repo: every curated spacing role has the same number on Android, Wear and iOS', () => {
  const entries = JSON.parse(read('MobileGarage/spacing-parity.json'))
  const repoSources = {
    android: parseKotlinDpTokens(read('MobileGarage/androidApp/src/main/java/com/chriscartland/garage/ui/theme/Spacing.kt')),
    wear: parseKotlinDpTokens(read('MobileGarage/wearApp/src/main/java/com/chriscartland/garage/wear/ui/theme/WearSpacing.kt')),
    ios: parseSwiftCGFloatTokens(read('MobileGarage/iosApp/Core/Theme/GarageSpacing.swift')),
  }
  // Scope sanity: the parsers saw real token files, not empty ones.
  assert.ok(repoSources.android.size >= 5, `android tokens parsed: ${repoSources.android.size}`)
  assert.ok(repoSources.wear.size >= 1, `wear tokens parsed: ${repoSources.wear.size}`)
  assert.ok(repoSources.ios.size >= 5, `ios tokens parsed: ${repoSources.ios.size}`)
  const checked = entries.filter((e) => e.role).length
  assert.ok(checked >= 5, `curated roles: ${checked}`)
  assert.deepEqual(checkSpacingParity(entries, repoSources), [])
})
