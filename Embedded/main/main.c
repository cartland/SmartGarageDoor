#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdio.h>
#include <string.h>

#include "my_math.h"

#define TAG "main.c"
#define MAX_BUTTON_TOKEN_LENGTH 256

unsigned int sensor_value = 0;

char button_token[MAX_BUTTON_TOKEN_LENGTH + 1] = "";

unsigned int read_sensor_value_ctr = 0;
unsigned int fetch_button_token_ctr = 0;

unsigned int read_sensor_value() {
    read_sensor_value_ctr = my_add(read_sensor_value_ctr, 1);
    return (read_sensor_value_ctr / 7) & 1;
}

bool read_sensors_heartbeat;
unsigned int heartbeat_millis = 5000;
unsigned int tick_count;
unsigned int last_tick_count;
void read_sensors(void *pvParameters) {
    while (1) {
        tick_count = xTaskGetTickCount();

        // Read sensor value
        unsigned int new_sensor_value = read_sensor_value();

        read_sensors_heartbeat = (tick_count - last_tick_count) > heartbeat_millis / portTICK_PERIOD_MS;
        if (sensor_value != new_sensor_value) {
            sensor_value = new_sensor_value;
            ESP_LOGI(TAG, "Change: Send sensor value %d to server", sensor_value);
            last_tick_count = tick_count;
        } else if (read_sensors_heartbeat) {
            ESP_LOGI(TAG, "Heartbeat: Send sensor value %d to server", sensor_value);
            last_tick_count = tick_count;
        }
        vTaskDelay(1000 / portTICK_PERIOD_MS);
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
    xTaskCreate(read_sensors, "read_sensors", 2048, NULL, 5, NULL);
    xTaskCreate(push_button, "push_button", 2048, NULL, 5, NULL);
}