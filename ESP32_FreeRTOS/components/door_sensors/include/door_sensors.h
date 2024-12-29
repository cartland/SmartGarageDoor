#ifndef DOOR_SENSORS_H
#define DOOR_SENSORS_H

#include <stdbool.h>
#include <stdint.h>

typedef struct {
    bool has_value;
    int level;
    uint32_t settled_tick;
    uint32_t tick_debounce_threshold;
} sensor_state_t;

typedef struct {
    void (*init)(sensor_state_t *state, uint32_t tick_debounce_threshold);
    bool (*debounce)(sensor_state_t *state, int level, uint32_t tick_count);
} sensor_debouncer_t;

extern sensor_debouncer_t sensor_debouncer;

#endif // DOOR_SENSORS_H
