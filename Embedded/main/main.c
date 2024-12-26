#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdio.h>
#include <string.h>

#include "door_button.h"
#include "door_sensors.h"
#include "garage_server.h"
#include "my_hal.h"
#include "my_math.h"

#define TAG "main.c"

QueueHandle_t xSensorQueue;
QueueHandle_t xButtonQueue;

static sensor_state_t sensors;

static int new_sensor_a;
static int new_sensor_b;
static bool a_changed;
static bool b_changed;

char new_button_token[MAX_BUTTON_TOKEN_LENGTH + 1] = "";

uint32_t HEARTBEAT_TICKS = pdMS_TO_TICKS(10000); // 10 seconds for debugging
TickType_t tick_count;
uint32_t tick_count_of_last_update;

void read_sensors(void *pvParameters) {
    while (1) {
        tick_count = xTaskGetTickCount();

        // Read sensor values
        new_sensor_a = my_hal_read_sensor_a();
        new_sensor_b = my_hal_read_sensor_b();
        a_changed = debounce_sensor_a(new_sensor_a, (uint32_t)tick_count);
        b_changed = debounce_sensor_b(new_sensor_b, (uint32_t)tick_count);
        if (a_changed) {
            sensors.a_level = new_sensor_a;
        }
        if (b_changed) {
            sensors.b_level = new_sensor_b;
        }

        if (a_changed || b_changed) {
            // If sensor values have changed, send them to the server
            xQueueSend(xSensorQueue, &sensors, 0);
            ESP_LOGI(TAG, "Change: Send sensor values a: %d, b: %d to server", sensors.a_level, sensors.b_level);
            tick_count_of_last_update = tick_count;
        } else if ((tick_count - tick_count_of_last_update) > HEARTBEAT_TICKS) {
            // If it is time to send a heartbeat, send the sensor values to the server
            xQueueSend(xSensorQueue, &sensors, 0);
            ESP_LOGI(TAG, "Heartbeat: Send sensor values a: %d, b: %d to server", sensors.a_level, sensors.b_level);
            tick_count_of_last_update = tick_count;
        }
        vTaskDelay(10 / portTICK_PERIOD_MS);
    }
}

sensor_state_t sensor_values_to_upload;
sensor_request_t sensor_request;
sensor_response_t sensor_response;

void upload_sensors(void *pvParameters) {
    while (1) {
        if (xQueueReceive(xSensorQueue, &sensor_values_to_upload, portMAX_DELAY)) {
            ESP_LOGI(TAG,
                     "Upload sensor values a: %d, b: %d",
                     sensor_values_to_upload.a_level,
                     sensor_values_to_upload.b_level);

            strncpy(sensor_request.device_id, "device_id", MAX_BUTTON_TOKEN_LENGTH);
            sensor_request.sensor_a = sensor_values_to_upload.a_level;
            sensor_request.sensor_b = sensor_values_to_upload.b_level;

            garage_server_send_sensor_values(&sensor_request, &sensor_response);

            ESP_LOGI(TAG,
                     "Received sensor values a: %d, b: %d",
                     sensor_response.sensor_a,
                     sensor_response.sensor_b);
        } else {
            ESP_LOGE(TAG, "Failed to receive sensor value");
        }
    }
}

button_request_t button_request;
button_response_t button_response;
void fetch_button_token(const char *old_button_token, char *new_button_token) {
    ESP_LOGI(TAG, "Fetch button token from server with %s", old_button_token);

    strncpy(button_request.device_id, "device_id", MAX_BUTTON_TOKEN_LENGTH);
    strncpy(button_request.button_token, old_button_token, MAX_BUTTON_TOKEN_LENGTH);

    garage_server_send_button_token(&button_request, &button_response);

    strncpy(new_button_token, button_response.button_token, MAX_BUTTON_TOKEN_LENGTH);
}

/**
 * Fetch button command from server and signal the xButtonQueue to push the button.
 */
void download_button_commands(void *pvParameters) {
    while (1) {
        ESP_LOGI(TAG, "Fetch button command from server");
        fetch_button_token(get_button_token(), new_button_token);

        if (should_push_button(new_button_token)) {
            xQueueSend(xButtonQueue, NULL, 0); // Signal the button to be pushed
        }
        save_button_token(new_button_token);

        vTaskDelay(5000 / portTICK_PERIOD_MS);
    }
}

/**
 * Push the button when a message is received in the xButtonQueue.
 */
void push_button(void *pvParameters) {
    while (1) {
        if (xQueueReceive(xButtonQueue, NULL, portMAX_DELAY)) {
            ESP_LOGI(TAG, "TODO: Push the button");
            my_hal_set_button(1);                 // Push the button
            vTaskDelay(500 / portTICK_PERIOD_MS); // 500 ms
            my_hal_set_button(0);                 // Release the button
        }
    }
}

void app_main(void) {
    my_hal_init();
    debounce_init(pdMS_TO_TICKS(50));
    xSensorQueue = xQueueCreate(10, sizeof(sensor_state_t));
    xButtonQueue = xQueueCreate(10, sizeof(void *));
    xTaskCreate(read_sensors, "read_sensors", 2048, NULL, 5, NULL);
    xTaskCreate(upload_sensors, "upload_sensors", 2048, NULL, 5, NULL);
    xTaskCreate(download_button_commands, "download_button_commands", 2048, NULL, 5, NULL);
    xTaskCreate(push_button, "push_button", 2048, NULL, 5, NULL);
}
