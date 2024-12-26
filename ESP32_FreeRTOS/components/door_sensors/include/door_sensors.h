#ifndef DOOR_SENSORS_H
#define DOOR_SENSORS_H

#include <stdbool.h>
#include <stdint.h>

typedef struct {
    int a_level;
    int b_level;
} sensor_state_t;

void debounce_init(uint32_t tick_debounce_threshold);

bool debounce_sensor_a(int level, uint32_t tick_count);
bool debounce_sensor_b(int level, uint32_t tick_count);

#endif // DOOR_SENSORS_H
