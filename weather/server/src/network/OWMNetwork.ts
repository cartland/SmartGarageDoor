import * as https from 'https';
import * as url from 'url';
import { OWMCurrentWeather } from '../model/OpenWeatherMapManager';

/**
 * Request data from Open Weather Map API.
 *
 * @param owmApiKey API key from https://openweathermap.org.
 * @param zipCountry zipCode,countryCode. Example: 10011,us
 * @param units "imperial" for Farenheit, "metric" for Celcius, "standard" for Kelvin.
 * @param language Example: "en".
 */
export const fetchCurrentOpenWeatherMapObservation = async (owmApiKey: string, zipCountry: string, units: string, language: string) => {
  return new Promise<OWMCurrentWeather>(function (resolve, reject) {
    const owmUrl = new url.URL("https://api.openweathermap.org/data/2.5/weather");
    owmUrl.searchParams.append('mode', 'json');
    owmUrl.searchParams.append('zip', zipCountry);
    owmUrl.searchParams.append('units', units);
    owmUrl.searchParams.append('lang', language);
    owmUrl.searchParams.append('appid', owmApiKey);
    console.debug(owmUrl.href);
    https.get(owmUrl.href, (res) => {
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
          const owmData: OWMCurrentWeather = JSON.parse(fullBody);
          if (owmData.cod !== 200) {
            console.error("Expected respond 'cod' 200, found: " + owmData.cod);
            reject(owmData);
            return;
          }
          resolve(owmData);
        } catch (e) {
          reject(e);
        }
      });
    });
  });
}
