// Unit tests for the pure snapshot renderer. Run with: node --test .github/scripts
import test from 'node:test'
import assert from 'node:assert/strict'
import { renderSnapshot, DISPLAY_ORDER } from './lib/format-snapshot.mjs'

const VERSION_NAMES = { 247: '2.16.37', 245: '2.16.35' }
const resolveVersionName = (vc) => VERSION_NAMES[vc] ?? '(no tag)'

// internal/alpha = completed full rollout; production = staged 10%; beta = empty.
// Deliberately out of display order in the input to prove the renderer sorts.
const TRACKS = [
  { track: 'production', releases: [{ status: 'inProgress', versionCodes: ['245'], userFraction: 0.1, name: '2.16.35' }] },
  { track: 'beta', releases: [] },
  { track: 'alpha', releases: [{ status: 'completed', versionCodes: ['247'], name: '2.16.37' }] },
  { track: 'internal', releases: [{ status: 'completed', versionCodes: ['247'], name: '2.16.37' }] },
]

function render(tracks = TRACKS) {
  return renderSnapshot({
    tracks,
    resolveVersionName,
    timestamp: '2026-06-09 14:31 UTC',
    trigger: 'manual (@tester)',
    packageName: 'com.chriscartland.garage',
  })
}

test('renders tracks in display order regardless of input order', () => {
  const { comment } = render()
  const positions = DISPLAY_ORDER.map((n) => comment.indexOf(`| ${n} |`))
  assert.ok(
    positions.every((p) => p >= 0),
    'every known track is present',
  )
  const sorted = [...positions].sort((a, b) => a - b)
  assert.deepEqual(positions, sorted, 'rows appear internal, alpha, beta, production')
})

test('maps versionCode to versionName via the resolver', () => {
  const { comment, machine } = render()
  assert.match(comment, /\| internal \| 247 \| 2\.16\.37 \|/)
  assert.deepEqual(machine.tracks.internal.versionNames, ['2.16.37'])
})

test('completed release with null userFraction shows 100%', () => {
  const { comment } = render()
  assert.match(comment, /\| internal \| 247 \| 2\.16\.37 \| completed \| 100% \|/)
})

test('staged rollout shows userFraction as a percentage', () => {
  const { comment, machine } = render()
  assert.match(comment, /\| production \| 245 \| 2\.16\.35 \| inProgress \| 10% \|/)
  assert.equal(machine.tracks.production.userFraction, 0.1)
})

test('empty track renders dashes and a null machine entry', () => {
  const { comment, machine } = render()
  assert.match(comment, /\| beta \| — \| — \| — \| — \|/)
  assert.equal(machine.tracks.beta, null)
})

test('unknown/custom tracks sort after the known four', () => {
  const withCustom = [...TRACKS, { track: 'qa-closed', releases: [{ status: 'completed', versionCodes: ['247'] }] }]
  const { comment } = render(withCustom)
  assert.ok(
    comment.indexOf('| production |') < comment.indexOf('| qa-closed |'),
    'custom track appears after production',
  )
})

test('unresolved versionCode falls back to the resolver default', () => {
  const tracks = [{ track: 'internal', releases: [{ status: 'completed', versionCodes: ['999'] }] }]
  const { comment } = render(tracks)
  assert.match(comment, /\| internal \| 999 \| \(no tag\) \| completed \| 100% \|/)
})

test('body carries the latest snapshot plus the "current state" preamble', () => {
  const { body, comment } = render()
  assert.match(body, /\*\*Current Play Store track state\*\*/)
  assert.ok(body.includes(comment), 'body embeds the full snapshot')
})

test('embedded JSON is valid and reflects the tracks', () => {
  const { comment } = render()
  const json = comment.match(/```json\n([\s\S]*?)\n```/)[1]
  const parsed = JSON.parse(json)
  assert.equal(parsed.tracks.alpha.status, 'completed')
  assert.equal(parsed.tracks.beta, null)
  assert.equal(parsed.packageName, 'com.chriscartland.garage')
})
