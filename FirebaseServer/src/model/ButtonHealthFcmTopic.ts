/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

const TOPIC_PREFIX = 'buttonHealth-';

/**
 * Distinct from buildTimestampToFcmTopic in src/model/FcmTopic.ts —
 * different device, different prefix, AND defensive try/catch around
 * decodeURIComponent because the button buildTimestamp is stored
 * URL-encoded in server config (since April 2021).
 *
 * Allowed FCM topic chars: [a-zA-Z0-9-_.~%]. Replacement char `.`
 * matches the door builder so a future maintainer reading both files
 * sees one rule.
 */
export function buildTimestampToButtonHealthFcmTopic(buildTimestamp: string): string {
  let decoded: string;
  try {
    decoded = decodeURIComponent(buildTimestamp);
  } catch (_err) {
    decoded = buildTimestamp;  // fall back to raw; sanitization makes it safe
  }
  if (decoded.length === 0) {
    throw new Error('buildTimestampToButtonHealthFcmTopic: empty buildTimestamp');
  }
  const sanitized = decoded.replace(/[^a-zA-Z0-9\-_.~%]/g, '.');
  return `${TOPIC_PREFIX}${sanitized}`;
}
