#ifndef MY_HAL_H
#define MY_HAL_H

typedef enum {
    G_HAL_SENSOR_A,
    G_HAL_SENSOR_B,
} garage_input_t;

typedef struct {
    void (*init)(void);
    // Input
    int (*read_sensor)(garage_input_t gpio);
    // Output
    void (*set_button)(int level);
} garage_hal_t;

extern garage_hal_t garage_hal;

#endif // MY_HAL_H
