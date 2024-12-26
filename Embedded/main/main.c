#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdio.h>
#include <string.h>

#include "my_hal.h"
#include "door_sensors.h"
#include "my_math.h"

#define TAG "main.c"
#define MAX_BUTTON_TOKEN_LENGTH 256

QueueHandle_t xSensorQueue;

static sensor_state_t sensors;

static int new_sensor_a;
static int new_sensor_b;
static bool a_changed;
static bool b_changed;

char button_token[MAX_BUTTON_TOKEN_LENGTH + 1] = "";

unsigned int read_sensor_value_ctr = 0;
unsigned int fetch_button_token_ctr = 0;

uint32_t HEARTBEAT_TICKS = pdMS_TO_TICKS(5000);
TickType_t tick_count;
uint32_t tick_count_of_last_update;

void read_sensors(void *pvParameters) {
    while (1) {
        tick_count = xTaskGetTickCount();

        // Read sensor values
        new_sensor_a = my_hal_read_sensor_a();
        new_sensor_b = my_hal_read_sensor_b();
        a_changed = debounce_sensor_a(new_sensor_a, (uint32_t) tick_count);
        b_changed = debounce_sensor_b(new_sensor_b, (uint32_t) tick_count);
        if (a_changed) {
            sensors.a_level = new_sensor_a;
        }
        if (b_changed) {
            sensors.b_level = new_sensor_b;
        }

        if (a_changed || b_changed) {
            // If sensor values have changed, send them to the server
            xQueueSend(xSensorQueue, &sensors, 0);
            ESP_LOGI(TAG, "Change: Send sensor value a: %d, b: %d to server", sensors.a_level, sensors.b_level);
            tick_count_of_last_update = tick_count;
        } else if ((tick_count - tick_count_of_last_update) > HEARTBEAT_TICKS) {
            // If it is time to send a heartbeat, send the sensor values to the server
            xQueueSend(xSensorQueue, &sensors, 0);
            ESP_LOGI(TAG, "Heartbeat: Send sensor value a: %d, b: %d to server", sensors.a_level, sensors.b_level);
            tick_count_of_last_update = tick_count;
        }
        vTaskDelay(10 / portTICK_PERIOD_MS);
    }
}

int fetch_button_token_result;
void fetch_button_token(char *new_button_token) {
    fetch_button_token_ctr++;
    fetch_button_token_result = snprintf(new_button_token, MAX_BUTTON_TOKEN_LENGTH, "button_token_%d", fetch_button_token_ctr / 2);

    if (fetch_button_token_result < 0 || fetch_button_token_result >= MAX_BUTTON_TOKEN_LENGTH) {
        if (fetch_button_token_result >= MAX_BUTTON_TOKEN_LENGTH) {
            ESP_LOGW(TAG, "Button token is too long, truncating to %d", MAX_BUTTON_TOKEN_LENGTH);
            new_button_token[MAX_BUTTON_TOKEN_LENGTH] = '\0';
        } else {
            ESP_LOGE(TAG, "Failed to generate button token");
            return;
        }
    }
}

char new_button_token[MAX_BUTTON_TOKEN_LENGTH + 1];
void push_button(void *pvParameters) {
    while (1) {
        ESP_LOGI(TAG, "Fetch button command from server");
        fetch_button_token(new_button_token);

        if (strcmp(button_token, new_button_token) == 0) {
            ESP_LOGI(TAG, "Button token is not changed");
        } else {
            // If button token is not empty, then push the button
            if (button_token[0] != '\0') {
                ESP_LOGI(TAG, "Push the button for %s", new_button_token);
            } else {
                ESP_LOGI(TAG, "Not pushing button because %s is the first token", new_button_token);
            }
            strncpy(button_token, new_button_token, MAX_BUTTON_TOKEN_LENGTH);
            ESP_LOGI(TAG, "Button token is now %s", button_token);
        }
        vTaskDelay(5000 / portTICK_PERIOD_MS);
    }
}

void app_main(void) {
    my_hal_init();
    xSensorQueue = xQueueCreate(10, sizeof(sensor_state_t));
    xTaskCreate(read_sensors, "read_sensors", 2048, NULL, 5, NULL);
    xTaskCreate(push_button, "push_button", 2048, NULL, 5, NULL);
}