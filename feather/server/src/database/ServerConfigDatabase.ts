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

import { TimeSeriesDatabase } from './TimeSeriesDatabase';

class ServerConfig {
  DATABASE = new TimeSeriesDatabase('configCurrent', 'configAll');
  CURRENT_KEY = 'current';

  async set(data) {
    await Config.DATABASE.save(Config.CURRENT_KEY, data);
  }

  async get(): Promise<any> {
    return Config.DATABASE.getCurrent(Config.CURRENT_KEY);
  }

  isRemoteButtonEnabled(config): boolean {
    if (config && config.hasOwnProperty('body') && config.body.hasOwnProperty('remoteButtonEnabled')) {
      return config.body.remoteButtonEnabled;
    }
    return false;
  }
};

export const Config = new ServerConfig();

