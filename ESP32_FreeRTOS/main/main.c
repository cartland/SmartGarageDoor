#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdio.h>
#include <string.h>

#include "door_button.h"
#include "door_sensors.h"
#include "garage_hal.h"
#include "garage_server.h"

#define TAG "main"
#define DEVICE_ID "device_id"

static void *void_pointer;
static QueueHandle_t xSensorQueue;
static QueueHandle_t xButtonQueue;

/**
 * Read sensor values and signal the xSensorQueue when they have changed.
 * Also send a regular heartbeat if the values do not change.
 */
void read_sensors(void *pvParameters) {
    static TickType_t tick_count;
    static uint32_t tick_count_of_last_update = 0;
    const static uint32_t HEARTBEAT_TICKS = pdMS_TO_TICKS(13000); // 13 seconds for debugging
    static int new_sensor_a;
    static int new_sensor_b;
    static bool a_changed;
    static bool b_changed;
    static sensor_state_t sensors;
    static BaseType_t xStatus;
    while (1) {
        tick_count = xTaskGetTickCount();

        // Read sensor values
        new_sensor_a = garage_hal.read_sensor(SENSOR_A_GPIO);
        new_sensor_b = garage_hal.read_sensor(SENSOR_B_GPIO);
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
            xStatus = xQueueSend(xSensorQueue, &sensors, 0);
            if (xStatus == pdPASS) {
                ESP_LOGI(TAG, "Change: Send sensor values a: %d, b: %d to xSensorQueue", sensors.a_level, sensors.b_level);
            } else {
                ESP_LOGE(TAG, "Failed to send sensor values a: %d, b: %d to xSensorQueue", sensors.a_level, sensors.b_level);
            }
            tick_count_of_last_update = tick_count;
        } else if (tick_count_of_last_update == 0) {
            // Make sure we send something after booting
            xStatus = xQueueSend(xSensorQueue, &sensors, 0);
            if (xStatus == pdPASS) {
                ESP_LOGI(TAG, "First Heartbeat: Send sensor values a: %d, b: %d to xSensorQueue", sensors.a_level, sensors.b_level);
            } else {
                ESP_LOGE(TAG, "Failed to send sensor values a: %d, b: %d to xSensorQueue", sensors.a_level, sensors.b_level);
            }
            tick_count_of_last_update = 1; // Ensure we don't send a heartbeat immediately again
        } else if ((tick_count - tick_count_of_last_update) > HEARTBEAT_TICKS) {
            // If it is time to send a heartbeat, send the sensor values to the server
            xStatus = xQueueSend(xSensorQueue, &sensors, 0);
            if (xStatus == pdPASS) {
                ESP_LOGI(TAG, "Heartbeat: Send sensor values a: %d, b: %d to xSensorQueue", sensors.a_level, sensors.b_level);
            } else {
                ESP_LOGE(TAG, "Failed to send sensor values a: %d, b: %d to xSensorQueue", sensors.a_level, sensors.b_level);
            }
            tick_count_of_last_update = tick_count;
        }
        vTaskDelay(10 / portTICK_PERIOD_MS); // 10 ms
    }
}

/**
 * Upload sensor values to the server.
 */
void upload_sensors(void *pvParameters) {
    static sensor_state_t sensor_values_to_upload;
    static sensor_request_t sensor_request;
    static sensor_response_t sensor_response;
    while (1) {
        if (xQueueReceive(xSensorQueue, &sensor_values_to_upload, portMAX_DELAY)) {
            ESP_LOGI(TAG,
                     "Upload sensor values a: %d, b: %d",
                     sensor_values_to_upload.a_level,
                     sensor_values_to_upload.b_level);

            snprintf(sensor_request.device_id, MAX_DEVICE_ID_LENGTH, "%s", DEVICE_ID);
            sensor_request.device_id[MAX_DEVICE_ID_LENGTH] = '\0';

            sensor_request.sensor_a = sensor_values_to_upload.a_level;
            sensor_request.sensor_b = sensor_values_to_upload.b_level;

            garage_server.send_sensor_values(&sensor_request, &sensor_response);

            ESP_LOGI(TAG,
                     "Received sensor values a: %d, b: %d",
                     sensor_response.sensor_a,
                     sensor_response.sensor_b);
        } else {
            ESP_LOGE(TAG, "Failed to receive sensor value");
        }
    }
}

/**
 * Fetch button command from server and signal the xButtonQueue to push the button.
 */
void download_button_commands(void *pvParameters) {
    static button_request_t button_request;
    static button_response_t button_response;
    static char old_button_token[MAX_BUTTON_TOKEN_LENGTH + 1] = "";
    static BaseType_t xStatus;

    while (1) {
        get_button_token(old_button_token);
        ESP_LOGI(TAG, "Fetch button token from server with %s", old_button_token);

        snprintf(button_request.device_id, MAX_DEVICE_ID_LENGTH, "%s", DEVICE_ID);
        snprintf(button_request.button_token, MAX_BUTTON_TOKEN_LENGTH + 1, "%s", old_button_token);

        garage_server.send_button_token(&button_request, &button_response);

        if (should_push_button(button_response.button_token)) {
            xStatus = xQueueSend(xButtonQueue, &void_pointer, 0); // Signal the button to be pushed
            if (xStatus == pdPASS) {
                ESP_LOGI(TAG, "Sent button push signal to xButtonQueue");
            } else {
                ESP_LOGE(TAG, "Failed to send button push signal to xButtonQueue");
            }
        }
        save_button_token(button_response.button_token);

        vTaskDelay(5000 / portTICK_PERIOD_MS); // 5 seconds
    }
}

/**
 * Push the button when a message is received in the xButtonQueue.
 */
void push_button(void *pvParameters) {
    while (1) {
        if (xQueueReceive(xButtonQueue, &void_pointer, portMAX_DELAY)) {
            ESP_LOGI(TAG, "TODO: Push the button");
            garage_hal.set_button(1);              // Push the button
            ESP_LOGI(TAG, "Button pushed");
            vTaskDelay(1000 / portTICK_PERIOD_MS); // 1000 ms
            garage_hal.set_button(0);              // Release the button
            ESP_LOGI(TAG, "Button released");
        }
    }
}

void log_hello(void *pvParameters) {
    while (1) {
        ESP_LOGI(TAG, "Hello, world!");
        vTaskDelay(10000 / portTICK_PERIOD_MS); // 10 seconds
    }
}

void app_main(void) {
    garage_hal.init();
    garage_server.init();
    debounce_init(pdMS_TO_TICKS(50));
    xSensorQueue = xQueueCreate(1, sizeof(sensor_state_t));
    xButtonQueue = xQueueCreate(1, sizeof(void *));
    xTaskCreate(log_hello, "log_hello", 2048, NULL, 5, NULL);
    xTaskCreate(read_sensors, "read_sensors", 2048, NULL, 5, NULL);
    xTaskCreate(upload_sensors, "upload_sensors", 2048, NULL, 5, NULL);
    xTaskCreate(download_button_commands, "download_button", 2048, NULL, 5, NULL);
    xTaskCreate(push_button, "push_button", 2048, NULL, 5, NULL);
}
