#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdio.h>
#include <string.h>

#include "door_sensors.h"
#include "door_button.h"
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
void upload_sensors(void *pvParameters) {
    while (1) {
        if (xQueueReceive(xSensorQueue, &sensor_values_to_upload, portMAX_DELAY)) {
            ESP_LOGI(TAG,
                     "TODO: Upload sensor values a: %d, b: %d",
                     sensor_values_to_upload.a_level,
                     sensor_values_to_upload.b_level);
            // TODO: Make HTTPS request to server.
        } else {
            ESP_LOGE(TAG, "Failed to receive sensor value");
        }
    }
}

void fetch_button_token(const char *old_button_token, char *new_button_token) {
    ESP_LOGI(TAG, "TODO: Fetch button token from server with %s", old_button_token);
    // TODO: Make HTTPS request to server to fetch button token

    // Simulate putting the fetched button token into new_button_token.
    snprintf(new_button_token,
             MAX_BUTTON_TOKEN_LENGTH,
             "button_token_%llu",
             (uint64_t)((((uint64_t)xTaskGetTickCount()) / configTICK_RATE_HZ) / 10) /* Changes every 10 seconds */);
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
