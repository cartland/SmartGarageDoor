#include "garage_config.h"
#ifdef CONFIG_USE_FAKE_GARAGE_HAL

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include <stdbool.h>
#include <stdio.h>

#include "garage_hal.h"

#define SENSOR_A_GPIO GPIO_NUM_2
#define SENSOR_B_GPIO GPIO_NUM_4
#define BUTTON_GPIO GPIO_NUM_5

const char *TAG = "fake_garage_hal";

// Initialize the hardware abstraction layer
static void garage_hal_init(void) {
    ESP_LOGI(TAG, "Initialize garage HAL");
}

static int garage_hal_read_sensor(garage_input_t gpio) {
    // Alternate values on different coprime periods
    int value;
    switch (gpio) {
    case G_HAL_SENSOR_A:
        value = (xTaskGetTickCount() / pdMS_TO_TICKS(13000)) % 2; // 0 or 1
        break;
    case G_HAL_SENSOR_B:
        value = (xTaskGetTickCount() / pdMS_TO_TICKS(37000)) % 2; // 0 or 1
        break;
    default:
        value = -1;
        break;
    }
    return value;
}

// Set the button level
static void garage_hal_set_button(int level) {
    ESP_LOGI(TAG, "Set button level: %d", level);
}

garage_hal_t garage_hal = {
    .init = garage_hal_init,
    .read_sensor = garage_hal_read_sensor,
    .set_button = garage_hal_set_button,
};

#endif // CONFIG_USE_FAKE_GARAGE_HAL
