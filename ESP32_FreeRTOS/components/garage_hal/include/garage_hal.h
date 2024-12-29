#ifndef MY_HAL_H
#define MY_HAL_H

#include "driver/gpio.h"

typedef enum {
    SENSOR_A_GPIO = GPIO_NUM_2,
    SENSOR_B_GPIO = GPIO_NUM_3,
    BUTTON_GPIO = GPIO_NUM_5,
} garage_gpio_t;

typedef struct {
    void (*init)(void);
    // Input
    int (*read_sensor)(garage_gpio_t gpio);
    // Output
    void (*set_button)(int level);
} garage_hal_t;

extern garage_hal_t garage_hal;

#endif // MY_HAL_H
