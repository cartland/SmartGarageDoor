#include <stdio.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/uart.h"
#include "esp_log.h"

#define TAG "main.c"

void beat_1(void *pvParameters) {
    while (1) {  
        ESP_LOGI(TAG, "Beat 1");
        vTaskDelay(1000 / portTICK_PERIOD_MS);
    }
}

void beat_2(void *pvParameters) {
    vTaskDelay(100 / portTICK_PERIOD_MS);
    while (1) {  
        ESP_LOGI(TAG, "  Beat 2");
        vTaskDelay(2000 / portTICK_PERIOD_MS);
    }
}

void beat_3(void *pvParameters) {
    vTaskDelay(200 / portTICK_PERIOD_MS);
    while (1) {  
        ESP_LOGI(TAG, "    Beat 3");
        vTaskDelay(3000 / portTICK_PERIOD_MS);
    }
}

void beat_4(void *pvParameters) {
    vTaskDelay(300 / portTICK_PERIOD_MS);
    while (1) {  
        ESP_LOGI(TAG, "      Beat 4");
        vTaskDelay(4000 / portTICK_PERIOD_MS);
    }
}

void app_main(void) {
    xTaskCreate(beat_1, "echo_task", 2048, NULL, 5, NULL);   
    xTaskCreate(beat_2, "echo_task", 2048, NULL, 5, NULL);
    xTaskCreate(beat_3, "echo_task", 2048, NULL, 5, NULL);
    xTaskCreate(beat_4, "echo_task", 2048, NULL, 5, NULL);
}