#ifndef GARAGE_SERVER_H
#define GARAGE_SERVER_H

void garage_server_init(void);

typedef struct {
    char *device_id;
    int sensor_a;
    int sensor_b;
} sensor_request_t;

typedef struct {
    char *device_id;
    int sensor_a;
    int sensor_b;
} sensor_response_t;

typedef struct {
    char *device_id;
    char *button_token;
} button_request_t;

typedef struct {
    char *device_id;
    char *button_token;
} button_response_t;

void garage_server_send_sensor_values(sensor_request_t *sensor_request, sensor_response_t *sensor_response);

void garage_server_send_button_token(button_request_t *button_request, button_response_t *button_response);

#endif // GARAGE_SERVER_H
