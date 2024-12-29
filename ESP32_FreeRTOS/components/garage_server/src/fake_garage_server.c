#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include <stdio.h>
#include <string.h>

#include "fake_garage_server.h"

#define TAG "fake_garage_server"

garage_server_t garage_server = {
    .init = fake_garage_server_init,
    .send_sensor_values = fake_garage_server_send_sensor_values,
    .send_button_token = fake_garage_server_send_button_token,
};

void fake_garage_server_init(void) {
    ESP_LOGI(TAG, "Initialize garage server");
}

void fake_garage_server_send_sensor_values(sensor_request_t *sensor_request, sensor_response_t *sensor_response) {
    ESP_LOGI(TAG,
             "Send sensor values to server: device_id: %s, sensor_a: %d, sensor_b: %d",
             sensor_request->device_id,
             sensor_request->sensor_a,
             sensor_request->sensor_b);

    strncpy(sensor_response->device_id, sensor_request->device_id, MAX_DEVICE_ID_LENGTH);
    sensor_response->device_id[MAX_DEVICE_ID_LENGTH] = '\0';

    sensor_response->sensor_a = sensor_request->sensor_a;
    sensor_response->sensor_b = sensor_request->sensor_b;
}

void fake_garage_server_send_button_token(button_request_t *button_request, button_response_t *button_response) {
    ESP_LOGI(TAG,
             "Send button token to server: device_id: %s, button_token: %s",
             button_request->device_id,
             button_request->button_token);

    strncpy(button_response->device_id, button_request->device_id, MAX_DEVICE_ID_LENGTH);
    button_response->device_id[MAX_DEVICE_ID_LENGTH] = '\0';
    // Simulate putting the fetched button token into new_button_token.
    snprintf(button_response->button_token,
             MAX_BUTTON_TOKEN_LENGTH,
             "button_token_%llu",
             (uint64_t)((((uint64_t)xTaskGetTickCount()) / configTICK_RATE_HZ) / 11) /* Increments every 11 seconds */);
    button_response->button_token[MAX_BUTTON_TOKEN_LENGTH] = '\0';
}
