/**
 * Copyright 2021 Chris Cartland. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const COLLECTION_CURRENT = 'eventsCurrent';
const COLLECTION_ALL = 'eventsAll';

import { TimeSeriesDatabase } from './TimeSeriesDatabase';

class SensorEventDatabase {
  DB = new TimeSeriesDatabase(COLLECTION_CURRENT, COLLECTION_ALL);

  async set(buildTimestamp: string, data: any) {
    await this.DB.save(buildTimestamp, data);
  }

  async get(buildTimestamp: string): Promise<any> {
    return this.DB.getCurrent(buildTimestamp);
  }

  async getRecentN(n: number): Promise<any> {
    return this.DB.getLatestN(n);
  }
};

export const DATABASE = new SensorEventDatabase();