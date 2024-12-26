#ifndef MY_HAL_H
#define MY_HAL_H

// Initialize hardware abstraction layer
void my_hal_init(void);

// Input
int my_hal_read_sensor_a(void);
int my_hal_read_sensor_b(void);

// Output
void my_hal_set_button(int level);

#endif // MY_HAL_H
