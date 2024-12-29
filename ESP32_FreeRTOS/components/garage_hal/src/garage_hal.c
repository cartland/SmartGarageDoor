#include "driver/gpio.h"
#include <stdbool.h>
#include <stdio.h>

#include "garage_hal.h"

static void my_hal_init(void) {
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
    gpio_set_level(BUTTON_GPIO, 0); // Default to 0.
}

static int my_hal_read_sensor(garage_gpio_t gpio) {
    return gpio_get_level(gpio);
}

static void my_hal_set_button(int level) {
    gpio_set_level(BUTTON_GPIO, level);
}

garage_hal_t garage_hal = {
    .init = my_hal_init,
    .read_sensor = my_hal_read_sensor,
    .set_button = my_hal_set_button,
};
