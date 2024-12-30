#ifndef CONFIG_USE_FAKE_GARAGE_SERVER

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include <stdio.h>
#include <string.h>

#include "real_garage_server.h"

#define TAG "real_garage_server"

extern const uint8_t server_root_cert_pem_start[] asm("_binary_server_root_cert_pem_start");
extern const uint8_t server_root_cert_pem_end[]   asm("_binary_server_root_cert_pem_end");

extern garage_server_t garage_server = {
    .init = real_garage_server_init,
    .send_sensor_values = real_garage_server_send_sensor_values,
    .send_button_token = real_garage_server_send_button_token,
};

void real_garage_server_init(void) {
    ESP_LOGI(TAG, "Initialize garage server");
    ESP_LOGI(TAG, "Server root certificate: %s", server_root_cert_pem_start);
}

void real_garage_server_send_sensor_values(sensor_request_t *sensor_request, sensor_response_t *sensor_response) {
    ESP_LOGI(TAG, "TODO: Send sensor values to server: device_id: %s, sensor_a: %d, sensor_b: %d",
             sensor_request->device_id,
             sensor_request->sensor_a,
             sensor_request->sensor_b);
}

void real_garage_server_send_button_token(button_request_t *button_request, button_response_t *button_response) {
    ESP_LOGI(TAG, "TODO: Send button token to server: device_id: %s, button_token: %s",
             button_request->device_id,
             button_request->button_token);
}

#endif // CONFIG_USE_FAKE_GARAGE_SERVER
