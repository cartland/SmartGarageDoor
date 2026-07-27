import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  DIRECTORY_IMAGE_TYPES,
  SCREENSHOT_COUNT,
  describePlan,
  planImageSync,
  validatePlan,
} from './lib/listing-images.mjs'

const WEAR_DIR = 'MobileGarage/distribution/playstore/wear'
const PHONE_DIR = 'MobileGarage/distribution/playstore/screenshots/phoneScreenshots'

const wearFiles = [
  `${WEAR_DIR}/03_submitted.png`,
  `${WEAR_DIR}/01_closed.png`,
  `${WEAR_DIR}/05_open.png`,
  `${WEAR_DIR}/02_holding.png`,
  `${WEAR_DIR}/04_moving.png`,
]

test('maps the wear directory to the API imageType name', () => {
  assert.equal(DIRECTORY_IMAGE_TYPES.wear, 'wearScreenshots')
})

test('orders files by name so upload order matches the intended story', () => {
  // Play renders screenshots in upload order, so this ordering is the
  // narrative a store visitor actually sees: at rest -> holding -> sent ->
  // door moving -> open.
  const [{ files }] = planImageSync(wearFiles, ['wearScreenshots'])
  assert.deepEqual(
    files.map((f) => f.split('/').pop()),
    ['01_closed.png', '02_holding.png', '03_submitted.png', '04_moving.png', '05_open.png'],
  )
})

test('returns one entry per selected type, in the requested order', () => {
  const files = [...wearFiles, `${PHONE_DIR}/01_home_light.png`]
  const plan = planImageSync(files, ['wearScreenshots', 'phoneScreenshots'])
  assert.deepEqual(plan.map((p) => p.imageType), ['wearScreenshots', 'phoneScreenshots'])
})

test('ignores files outside the selected types', () => {
  const files = [...wearFiles, `${PHONE_DIR}/01_home_light.png`]
  const [{ files: planned }] = planImageSync(files, ['wearScreenshots'])
  assert.equal(planned.length, 5)
  assert.ok(planned.every((f) => f.includes('/wear/')))
})

test('ignores non-image files sitting in a curated directory', () => {
  const files = [...wearFiles, `${WEAR_DIR}/README.md`, `${WEAR_DIR}/.DS_Store`]
  const [{ files: planned }] = planImageSync(files, ['wearScreenshots'])
  assert.equal(planned.length, 5)
})

test('ignores unrecognised directories', () => {
  const files = [...wearFiles, 'MobileGarage/distribution/playstore/src/icon.svg']
  const [{ files: planned }] = planImageSync(files, ['wearScreenshots'])
  assert.equal(planned.length, 5)
})

test('excludes the GENERATED staging tree even though it also has a wear/ dir', () => {
  // MobileGarage/screenshots/store/wear/ holds every candidate shot, including
  // ones deliberately kept out of the store. Matching on the parent directory
  // name alone would upload them; only the curated tree mirrors the listing.
  const files = [
    ...wearFiles,
    'MobileGarage/screenshots/store/wear/wear-connecting.png',
    'MobileGarage/screenshots/store/wear/wear-sign_in_error.png',
  ]
  const [{ files: planned }] = planImageSync(files, ['wearScreenshots'])
  assert.equal(planned.length, 5)
  assert.ok(planned.every((f) => f.startsWith('MobileGarage/distribution/playstore/')))
})

test('refuses an empty set rather than silently wiping the listing', () => {
  // The dangerous case: a typo'd path yields zero files, and a naive
  // implementation would deleteall + upload nothing, clearing the section.
  assert.throws(
    () => planImageSync([], ['wearScreenshots']),
    /Refusing to sync an empty set/,
  )
})

test('refuses more screenshots than Play accepts', () => {
  const tooMany = Array.from(
    { length: SCREENSHOT_COUNT.max + 1 },
    (_, i) => `${WEAR_DIR}/${String(i).padStart(2, '0')}_shot.png`,
  )
  assert.throws(() => planImageSync(tooMany, ['wearScreenshots']), /at most 8/)
})

test('accepts exactly the maximum', () => {
  const exactly = Array.from(
    { length: SCREENSHOT_COUNT.max },
    (_, i) => `${WEAR_DIR}/${String(i).padStart(2, '0')}_shot.png`,
  )
  const [{ files }] = planImageSync(exactly, ['wearScreenshots'])
  assert.equal(files.length, SCREENSHOT_COUNT.max)
})

test('rejects an unsupported imageType instead of guessing', () => {
  assert.throws(() => planImageSync(wearFiles, ['tvScreenshots']), /Unsupported imageType/)
})

test('rejects an empty selection', () => {
  assert.throws(() => planImageSync(wearFiles, []), /No imageTypes selected/)
})

test('validatePlan is exported for callers that build a plan by hand', () => {
  assert.throws(
    () => validatePlan([{ imageType: 'wearScreenshots', files: [] }]),
    /Refusing to sync an empty set/,
  )
})

test('dry-run description says nothing was published', () => {
  const plan = planImageSync(wearFiles, ['wearScreenshots'])
  const text = describePlan(plan, { apply: false, language: 'en-US' })
  assert.match(text, /DRY RUN/)
  assert.match(text, /No changes were published/)
  assert.match(text, /01_closed\.png/)
})

test('apply description does not claim to be a dry run', () => {
  const plan = planImageSync(wearFiles, ['wearScreenshots'])
  const text = describePlan(plan, { apply: true, language: 'en-US' })
  assert.match(text, /Committing listing images/)
  assert.doesNotMatch(text, /DRY RUN/)
  assert.doesNotMatch(text, /No changes were published/)
})
