/**
 * Air quality data returned by the server.
 *
 * No nested fields.
 * Data is cleaned up before serving (remove trailing whitespace).
 */
export interface AirQualityObservation {
  DateObserved: string,   // "2021-02-13"
  HourObserved: number,   // 22
  LocalTimeZone: string,  // "PST"
  ReportingArea: string,  // "San Francisco"
  StateCode: string,      // "CA"
  Latitude: number,       // 37.75
  Longitude: number,      // -122.43
  ParameterName: string,  // "PM2.5", "O3"
  AQI: number,            // 19
  CategoryNumber: number, // 1
  CategoryName: string,   // "Good"
}