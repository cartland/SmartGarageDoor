import * as https from 'https';
import * as url from 'url';
import { AirNowApiObservation } from '../model/AirNowManager';

/**
 * Request data from AirNowApi.gov.
 *
 * @param airNowApiKey API key from airnowapi.org.
 * @param zipCode US ZIP Code.
 * @param miles Maximum distance from Zip Code.
 */
export const getCurrentAirNowObservation = async (airNowApiKey: string, zipCode: string, miles: string) => {
  return new Promise<AirNowApiObservation[]>(function (resolve, reject) {
    const airNowUrl = new url.URL("https://www.airnowapi.org/aq/observation/zipCode/current/");
    airNowUrl.searchParams.append('format', 'application/json');
    airNowUrl.searchParams.append('zipCode', zipCode);
    airNowUrl.searchParams.append('distance', miles);
    airNowUrl.searchParams.append('API_KEY', airNowApiKey);
    https.get(airNowUrl.href, (res) => {
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
          const observations: AirNowApiObservation[] = JSON.parse(fullBody);
          resolve(observations);
        } catch (e) {
          reject(e);
        }
      });
    });
  });
}
