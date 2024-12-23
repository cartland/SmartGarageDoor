#include <stdio.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"

#define TAG "main.c"

void read_pin(void *pvParameters) {
    while (1) {
        ESP_LOGI(TAG, "Read pin");
        vTaskDelay(1000 / portTICK_PERIOD_MS);
    }
}

void write_pin(void *pvParameters) {
    while (1) {
        ESP_LOGI(TAG, "Write pin");
        vTaskDelay(5000 / portTICK_PERIOD_MS);
    }
}

void app_main(void) {
    xTaskCreate(read_pin, "read_pin", 2048, NULL, 5, NULL);
    xTaskCreate(write_pin, "write_pin", 2048, NULL, 5, NULL);
}