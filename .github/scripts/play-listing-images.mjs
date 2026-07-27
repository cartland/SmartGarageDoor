// Syncs the curated Play Store listing screenshots to the live store listing.
//
// This is the automation for what used to be a manual Play Console step: the
// release workflows ship the AAB and whatsnew/, but listing IMAGES were
// uploaded by hand, so the store could drift from the app (it did — the Wear
// shots advertised a "Tap door to arm" interaction that no longer exists).
//
// Safety model, in layers:
//   1. workflow_dispatch only — never fires on push or on a release.
//   2. apply=false by default. A dry run performs the ENTIRE upload into a
//      Play edit and then ABANDONS it. Play edits are transactional, so this
//      exercises auth, permissions, image validation and count limits with
//      zero public effect. Nothing is a "hope it works" step.
//   3. The plan is validated before any call (see lib/listing-images.mjs):
//      an empty set is refused rather than silently clearing a listing
//      section, and only the CURATED tree is eligible.
//
// Inputs (env):
//   GOOGLE_PLAY_SERVICE_ACCOUNT_JSON  Service account JSON (same secret the
//                                     AAB upload uses)
//   PACKAGE_NAME                      e.g. com.chriscartland.garage
//   IMAGE_TYPES                       Comma-separated Play imageTypes
//   LISTING_LANGUAGE                  BCP-47 listing language, e.g. en-US
//   APPLY                             'true' to commit; anything else = dry run
//
// Pure planning/validation lives in lib/listing-images.mjs and is unit-tested
// by play-listing-images.test.mjs; this file only does I/O.

import pkg from 'googleapis'
import { createReadStream, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import {
  CURATED_ROOT,
  describePlan,
  planImageSync,
} from './lib/listing-images.mjs'

const { google } = pkg

const packageName = process.env.PACKAGE_NAME || 'com.chriscartland.garage'
const saJson = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
const language = process.env.LISTING_LANGUAGE || 'en-US'
const apply = process.env.APPLY === 'true'
const imageTypes = (process.env.IMAGE_TYPES || 'wearScreenshots')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean)

if (!saJson) {
  console.error('Missing GOOGLE_PLAY_SERVICE_ACCOUNT_JSON')
  process.exit(1)
}

/** Every file under the curated root, recursively, as repo-relative paths. */
function listCuratedFiles(dir = CURATED_ROOT) {
  const out = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) out.push(...listCuratedFiles(full))
    else out.push(full)
  }
  return out
}

const plan = planImageSync(listCuratedFiles(), imageTypes)
console.log(describePlan(plan, { apply, language }))
console.log('')

const auth = new google.auth.GoogleAuth({
  credentials: JSON.parse(saJson),
  scopes: ['https://www.googleapis.com/auth/androidpublisher'],
})
const publisher = google.androidpublisher({ version: 'v3', auth })

const { data: edit } = await publisher.edits.insert({ packageName })
const editId = edit.id
console.log(`Opened edit ${editId}`)

let committed = false
try {
  for (const { imageType, files } of plan) {
    const { data: before } = await publisher.edits.images.list({
      packageName,
      editId,
      language,
      imageType,
    })
    const existing = before.images?.length ?? 0
    console.log(`${imageType}: ${existing} live -> ${files.length} new`)

    // Replace rather than append: the listing should mirror the curated set
    // exactly, and Play has no "set" operation. Both calls are scoped to this
    // edit, so nothing is visible until (and unless) the edit is committed.
    await publisher.edits.images.deleteall({ packageName, editId, language, imageType })
    for (const file of files) {
      await publisher.edits.images.upload({
        packageName,
        editId,
        language,
        imageType,
        media: { mimeType: 'image/png', body: createReadStream(file) },
      })
      console.log(`  uploaded ${file}`)
    }
  }

  if (apply) {
    await publisher.edits.commit({ packageName, editId })
    committed = true
    console.log(`\nCommitted edit ${editId}. Listing images are live.`)
  }
} finally {
  // Abandon on a dry run, and also on failure mid-apply, so a half-written
  // edit can never linger and block the next run or a release.
  if (!committed) {
    await publisher.edits.delete({ packageName, editId }).catch((err) => {
      console.error(`Warning: could not abandon edit ${editId}: ${err.message}`)
    })
    console.log(`\nAbandoned edit ${editId}. Nothing was published.`)
  }
}
