// Pure planning logic for the Play Store listing-image sync. No I/O, no
// network, no filesystem — the caller passes in a file list and gets back a
// validated upload plan. That split is what makes the risky part (mutating a
// public store listing) testable without touching the store.

/**
 * Curated directory name -> Play Developer API `imageType`.
 *
 * The phone/tablet directories were already named after the API's own
 * imageType values when the curated set was created; `wear` is the one that
 * needs mapping (the API calls it `wearScreenshots`).
 */
export const DIRECTORY_IMAGE_TYPES = Object.freeze({
  phoneScreenshots: 'phoneScreenshots',
  sevenInchScreenshots: 'sevenInchScreenshots',
  tenInchScreenshots: 'tenInchScreenshots',
  wear: 'wearScreenshots',
})

/** Every imageType this tool knows how to sync. */
export const SUPPORTED_IMAGE_TYPES = Object.freeze(
  Object.values(DIRECTORY_IMAGE_TYPES),
)

/**
 * Only files under here are eligible. This is load-bearing, not decoration:
 * the GENERATED staging tree has a `wear/` directory too
 * (`MobileGarage/screenshots/store/wear/`), so matching on the parent
 * directory name alone would happily upload un-curated candidate shots to the
 * live store. The curated tree is the only thing that mirrors the listing.
 */
export const CURATED_ROOT = 'MobileGarage/distribution/playstore'

/**
 * Play's own limits on a screenshot set. Uploading outside these fails at
 * `edits.commit`, which is a slow and confusing place to find out, so the
 * plan is validated up front instead.
 */
export const SCREENSHOT_COUNT = Object.freeze({ min: 1, max: 8 })

const IMAGE_EXTENSIONS = /\.(png|jpg|jpeg)$/i

/** Basename of a path, without depending on node:path (keeps this pure). */
function basename(filePath) {
  const parts = filePath.split('/')
  return parts[parts.length - 1]
}

/**
 * The Play imageType a file belongs to, or null if it is not a curated
 * listing image. Anchored to [CURATED_ROOT] — see the note there.
 */
function imageTypeFor(filePath, curatedRoot) {
  const prefix = `${curatedRoot}/`
  if (!filePath.startsWith(prefix)) return null
  const parts = filePath.slice(prefix.length).split('/')
  if (parts.length < 2) return null
  return DIRECTORY_IMAGE_TYPES[parts[parts.length - 2]] ?? null
}

/**
 * Group image files into per-imageType upload batches.
 *
 * Files are ordered by filename, which is why the curated sets use numeric
 * prefixes (`01_closed.png`): the Play listing shows screenshots in upload
 * order, so filename order IS the story order a visitor sees. Alphabetical
 * ordering of undecorated names would silently reshuffle that narrative.
 *
 * @param {string[]} files Repo-relative paths under the curated directories.
 * @param {string[]} selected imageTypes to include.
 * @param {{curatedRoot?: string}} [options]
 * @returns {{imageType: string, files: string[]}[]} one entry per selected
 *   type, in the order given by `selected`.
 */
export function planImageSync(files, selected, { curatedRoot = CURATED_ROOT } = {}) {
  if (!Array.isArray(selected) || selected.length === 0) {
    throw new Error('No imageTypes selected — nothing to sync.')
  }

  const unknown = selected.filter((t) => !SUPPORTED_IMAGE_TYPES.includes(t))
  if (unknown.length > 0) {
    throw new Error(
      `Unsupported imageType(s): ${unknown.join(', ')}. ` +
        `Supported: ${SUPPORTED_IMAGE_TYPES.join(', ')}`,
    )
  }

  const byType = new Map(selected.map((t) => [t, []]))
  for (const file of files) {
    if (!IMAGE_EXTENSIONS.test(file)) continue
    const imageType = imageTypeFor(file, curatedRoot)
    if (!imageType) continue
    if (!byType.has(imageType)) continue
    byType.get(imageType).push(file)
  }

  const plan = selected.map((imageType) => ({
    imageType,
    files: byType
      .get(imageType)
      .slice()
      .sort((a, b) => basename(a).localeCompare(basename(b))),
  }))

  validatePlan(plan)
  return plan
}

/**
 * Reject a plan Play would reject — but before anything is uploaded.
 *
 * An empty set is an error rather than a no-op on purpose: syncing an
 * imageType with zero files would `deleteall` and upload nothing, silently
 * wiping that section of the live listing. Removing screenshots should be a
 * deliberate Console action, not the accidental result of a typo'd path.
 *
 * @param {{imageType: string, files: string[]}[]} plan
 */
export function validatePlan(plan) {
  for (const { imageType, files } of plan) {
    if (files.length < SCREENSHOT_COUNT.min) {
      throw new Error(
        `No images found for ${imageType}. Refusing to sync an empty set — ` +
          'that would clear the live listing. Check the curated directory path.',
      )
    }
    if (files.length > SCREENSHOT_COUNT.max) {
      throw new Error(
        `${imageType} has ${files.length} images; Play allows at most ` +
          `${SCREENSHOT_COUNT.max}. Curate the set down before syncing.`,
      )
    }
  }
}

/**
 * Human-readable summary of what a run did (or, on a dry run, would do).
 * Kept here so it is unit-testable alongside the plan it describes.
 *
 * @param {{imageType: string, files: string[]}[]} plan
 * @param {{apply: boolean, language: string}} options
 */
export function describePlan(plan, { apply, language }) {
  const lines = [
    apply
      ? `Committing listing images for ${language}:`
      : `DRY RUN — uploading into a throwaway edit for ${language}, then abandoning it:`,
  ]
  for (const { imageType, files } of plan) {
    lines.push(`  ${imageType} (${files.length}):`)
    for (const file of files) lines.push(`    - ${basename(file)}`)
  }
  if (!apply) {
    lines.push('')
    lines.push('No changes were published. Re-run with apply=true to commit.')
  }
  return lines.join('\n')
}
