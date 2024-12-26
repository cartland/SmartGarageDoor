#ifndef MY_HAL_H
#define MY_HAL_H

#include "driver/gpio.h"

#define SENSOR_A_GPIO GPIO_NUM_2
#define SENSOR_B_GPIO GPIO_NUM_3
#define BUTTON_GPIO GPIO_NUM_5

// Initialize hardware abstraction layer
void my_hal_init(void);

// Input
int my_hal_read_sensor_a(void);
int my_hal_read_sensor_b(void);

// Output
void my_hal_set_button(int level);

#endif // MY_HAL_H
