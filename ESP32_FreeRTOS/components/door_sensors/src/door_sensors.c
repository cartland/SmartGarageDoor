#include <stdio.h>

#include "door_sensors.h"

void debounce_init(sensor_state_t *sensor_state, uint32_t tick_debounce_threshold) {
    sensor_state->tick_debounce_threshold = tick_debounce_threshold;
    sensor_state->has_value = false;
}

bool debounce_sensor(sensor_state_t *sensor_state, int level, uint32_t tick_count) {
    if (!sensor_state->has_value) {
        // Always return true if this is the first value
        sensor_state->has_value = true;
        sensor_state->level = level;
        sensor_state->pending_level = level;
        sensor_state->settled_tick = tick_count;
        return true;
    }
    if (sensor_state->level == level) {
        // If the value has not changed, update the settled tick "time"
        sensor_state->pending_level = level;
        sensor_state->settled_tick = tick_count;
        return false;
    }
    if (sensor_state->pending_level != level) {
        // This is a new value, start tracking it
        sensor_state->pending_level = level;
        sensor_state->settled_tick = tick_count;
        return false;
    }
    if (tick_count - sensor_state->settled_tick < sensor_state->tick_debounce_threshold) {
        // If the value has changed, but not for long enough, ignore it
        return false;
    }
    // Return true if the value has changed and settled:
    //   - This is not the first value
    //   - The value has changed
    //   - The value has been stable (level == pending_level)
    //   - The value has been stable for the debounce threshold
    sensor_state->level = level;
    sensor_state->settled_tick = tick_count;
    return true;
}

sensor_debouncer_t sensor_debouncer = {
    .init = debounce_init,
    .debounce = debounce_sensor,
};
