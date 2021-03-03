#pragma once

String wifiSetup(String wifiSSID, String wifiPassword);

void wget(String &url, int port, char *buff);

void wget(String &host, String &path, int port, char *buff);
