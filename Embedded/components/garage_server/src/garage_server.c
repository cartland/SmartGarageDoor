#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include <stdio.h>
#include <string.h>

#include "garage_server.h"

#define TAG "garage_server.c"

void garage_server_init(void) {
    // TODO: Initialize garage server
    ESP_LOGI(TAG, "TODO: Initialize garage server");
}

void garage_server_send_sensor_values(sensor_request_t *sensor_request, sensor_response_t *sensor_response) {
    // TODO: Send sensor values to server
    ESP_LOGI(TAG,
             "TODO: Send sensor values to server: device_id: %s, sensor_a: %d, sensor_b: %d",
             sensor_request->device_id,
             sensor_request->sensor_a,
             sensor_request->sensor_b);

    strncpy(sensor_response->device_id, sensor_request->device_id, 256);
    sensor_response->sensor_a = sensor_request->sensor_a;
    sensor_response->sensor_b = sensor_request->sensor_b;
}

void garage_server_send_button_token(button_request_t *button_request, button_response_t *button_response) {
    // TODO: Send button token to server
    ESP_LOGI(TAG,
             "TODO: Send button token to server: device_id: %s, button_token: %s",
             button_request->device_id,
             button_request->button_token);

    strncpy(button_response->device_id, button_request->device_id, 256);
    // Simulate putting the fetched button token into new_button_token.
    snprintf(button_response->button_token,
             256,
             "button_token_%llu",
             (uint64_t)((((uint64_t)xTaskGetTickCount()) / configTICK_RATE_HZ) / 10) /* Changes every 10 seconds */);
}
