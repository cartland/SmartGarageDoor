#ifndef FAKE_GARAGE_SERVER_H
#define FAKE_GARAGE_SERVER_H

#include "garage_server.h"

// extern garage_server_t garage_server;
void fake_garage_server_init(void);
void fake_garage_server_send_sensor_values(sensor_request_t *sensor_request, sensor_response_t *sensor_response);
void fake_garage_server_send_button_token(button_request_t *button_request, button_response_t *button_response);

#endif // FAKE_GARAGE_SERVER_H
