import * as https from 'https';
import * as url from 'url';
import { IQAirObservation } from '../model/IQAirManager';

/**
 * Request data from iqair.com.
 *
 * @param iqAirApiKey API Key for iqair.com.
 * @param lat Latitude.
 * @param lon Longitude.
 */
export const getCurrentIQAirObservation = async (iqAirApiKey: string, lat: string, lon: string) => {
  return new Promise<IQAirObservation>(function (resolve, reject) {
    const iqAirUrl = new url.URL("https://api.airvisual.com/v2/nearest_city");
    iqAirUrl.searchParams.append('format', 'application/json');
    iqAirUrl.searchParams.append('lat', lat);
    iqAirUrl.searchParams.append('lon', lon);
    iqAirUrl.searchParams.append('key', iqAirApiKey);
    https.get(iqAirUrl.href, (res) => {
      const body = [];
      res.on('data', function (chunk) {
        body.push(chunk);
      });
      res.on('end', function () {
        try {
          const fullBody = Buffer.concat(body).toString();
          if (res.statusCode >= 400) {
            reject(fullBody);
            return;
          }
          const observation: IQAirObservation = JSON.parse(fullBody);
          resolve(observation);
        } catch (e) {
          reject(e);
        }
      });
    });
  });
}
