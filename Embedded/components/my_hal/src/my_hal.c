#include "driver/gpio.h"
#include <stdbool.h>
#include <stdio.h>

#include "my_hal.h"

#define SENSOR_A_GPIO GPIO_NUM_2
#define SENSOR_B_GPIO GPIO_NUM_3
#define BUTTON_GPIO GPIO_NUM_5

void my_hal_init(void) {
    gpio_config_t io_conf;

    // Configure input pins
    io_conf.intr_type = GPIO_INTR_DISABLE;
    io_conf.mode = GPIO_MODE_INPUT;
    io_conf.pin_bit_mask = (1ULL << SENSOR_A_GPIO) | (1ULL << SENSOR_B_GPIO);
    io_conf.pull_down_en = GPIO_PULLDOWN_DISABLE;
    io_conf.pull_up_en = GPIO_PULLUP_ENABLE;
    gpio_config(&io_conf);

    // Configure output pin
    io_conf.intr_type = GPIO_INTR_DISABLE;
    io_conf.mode = GPIO_MODE_OUTPUT;
    io_conf.pin_bit_mask = (1ULL << BUTTON_GPIO);
    io_conf.pull_down_en = GPIO_PULLDOWN_DISABLE;
    io_conf.pull_up_en = GPIO_PULLUP_DISABLE;
    gpio_config(&io_conf);
}

int my_hal_read_sensor_a(void) {
    return gpio_get_level(SENSOR_A_GPIO);
}

int my_hal_read_sensor_b(void) {
    return gpio_get_level(SENSOR_B_GPIO);
}

void my_hal_set_button(int level) {
    gpio_set_level(BUTTON_GPIO, level);
}
