#include "garage_config.h"
#ifndef CONFIG_USE_FAKE_GARAGE_HAL

#include "driver/gpio.h"
#include <stdbool.h>
#include <stdio.h>

#include "garage_hal.h"

#define SENSOR_A_GPIO GPIO_NUM_25
#define SENSOR_B_GPIO GPIO_NUM_26
#define BUTTON_GPIO GPIO_NUM_27

// Initialize the hardware abstraction layer
static void garage_hal_init(void) {
    gpio_config_t io_conf;

    // Configure input pins
    io_conf.intr_type = GPIO_INTR_DISABLE;
    io_conf.mode = GPIO_MODE_INPUT;
    io_conf.pin_bit_mask = (1ULL << SENSOR_A_GPIO) | (1ULL << SENSOR_B_GPIO);
    io_conf.pull_down_en = GPIO_PULLDOWN_DISABLE;
    io_conf.pull_up_en = GPIO_PULLUP_ENABLE;
    if (gpio_config(&io_conf) != ESP_OK) {
        printf("Failed to configure input GPIO pins\n");
        return;
    }

    // Configure output pin
    io_conf.intr_type = GPIO_INTR_DISABLE;
    io_conf.mode = GPIO_MODE_OUTPUT;
    io_conf.pin_bit_mask = (1ULL << BUTTON_GPIO);
    io_conf.pull_down_en = GPIO_PULLDOWN_DISABLE;
    io_conf.pull_up_en = GPIO_PULLUP_DISABLE;
    if (gpio_config(&io_conf) != ESP_OK) {
        printf("Failed to configure output GPIO pin\n");
        return;
    }
    gpio_set_level(BUTTON_GPIO, 0); // Default to 0.
}

// Read the sensor value
static int garage_hal_read_sensor(garage_input_t gpio) {
    switch (gpio) {
    case G_HAL_SENSOR_A:
        return gpio_get_level(SENSOR_A_GPIO);
    case G_HAL_SENSOR_B:
        return gpio_get_level(SENSOR_B_GPIO);
    default:
        return -1;
    }
}

// Set the button level
static void garage_hal_set_button(int level) {
    gpio_set_level(BUTTON_GPIO, level);
}

garage_hal_t garage_hal = {
    .init = garage_hal_init,
    .read_sensor = garage_hal_read_sensor,
    .set_button = garage_hal_set_button,
};

#endif // CONFIG_USE_FAKE_GARAGE_HAL
