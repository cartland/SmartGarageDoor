#ifndef REAL_GARAGE_SERVER_H
#define REAL_GARAGE_SERVER_H

#include "garage_server.h"

void real_garage_server_send_sensor_values(sensor_request_t *sensor_request, sensor_response_t *sensor_response);
void real_garage_server_send_button_token(button_request_t *button_request, button_response_t *button_response);

#endif // REAL_GARAGE_SERVER_H
