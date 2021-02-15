#pragma once

// secrets.h

// WiFi.
#define WIFI_SSID "YOUR_WIFI_SSID"
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"

// Open Weather Maps.
#define OWM_CURRENT_URL "https://us-central1-YOUR_FIREBASE_PROJECT_ID.cloudfunctions.net/current_weather"
#define OWM_API_KEY "YOUR_OPEN_WEATHER_MAPS_API_KEY"
#define OWM_UNITS "imperial"
#define OWM_ZIP_COUNTRY "10011,us"
/*
Arabic - ar, Bulgarian - bg, Catalan - ca, Czech - cz, German - de, Greek - el,
English - en, Persian (Farsi) - fa, Finnish - fi, French - fr, Galician - gl,
Croatian - hr, Hungarian - hu, Italian - it, Japanese - ja, Korean - kr,
Latvian - la, Lithuanian - lt, Macedonian - mk, Dutch - nl, Polish - pl,
Portuguese - pt, Romanian - ro, Russian - ru, Swedish - se, Slovak - sk,
Slovenian - sl, Spanish - es, Turkish - tr, Ukrainian - ua, Vietnamese - vi,
Chinese Simplified - zh_cn, Chinese Traditional - zh_tw.
*/
#define OWM_LANGUAGE "en"

// Air Now API.
#define AIR_QUALITY_CURRENT_URL "https://us-central1-YOUR_FIREBASE_PROJECT_ID.cloudfunctions.net/current_air_quality_observation"
#define ZIP_CODE "10011"
#define AIR_NOW_API_KEY "YOUR_AIR_NOW_API_KEY"
#define DISTANCE_MILES 25

// update the weather at this interval, in minutes
#define UPDATE_INTERVAL 15
