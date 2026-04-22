/**
 * Copyright 2026 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { RemoteButtonRequestErrorDatabase } from '../../src/database/RemoteButtonRequestErrorDatabase';

export class FakeRemoteButtonRequestErrorDatabase implements RemoteButtonRequestErrorDatabase {
  /** Audit log of all save() calls. This DB is write-only in production. */
  readonly saved: Array<[string, any]> = [];

  async save(buildTimestamp: string, data: any): Promise<void> {
    this.saved.push([buildTimestamp, data]);
  }

  /** Test-only helper: wipe audit log. */
  clear(): void {
    this.saved.length = 0;
  }
}
