#include <stdio.h>

#include "door_sensors.h"

static uint32_t debounce_threshold;
static uint32_t settled_tick_a;
static uint32_t settled_tick_b;

static bool sensor_a_has_value;
static bool sensor_b_has_value;

static int sensor_a_level;
static int sensor_b_level;

void debounce_init(uint32_t tick_debounce_threshold) {
    debounce_threshold = tick_debounce_threshold;
    sensor_a_has_value = false;
    sensor_b_has_value = false;
}

bool debounce_sensor_a(int level, uint32_t tick_count) {
    if (!sensor_a_has_value) {
        // Always return true if this is the first value
        sensor_a_has_value = true;
        sensor_a_level = level;
        settled_tick_a = tick_count;
        return true;
    }
    if (sensor_a_level == level) {
        // If the value has not changed, update the settled tick "time"
        settled_tick_a = tick_count;
        return false;
    }
    if (tick_count - settled_tick_a < debounce_threshold) {
        // If the value has changed, but not for long enough, ignore it
        return false;
    }
    // Return true if the value has changed
    sensor_a_level = level;
    settled_tick_a = tick_count;
    return true;
}

bool debounce_sensor_b(int level, uint32_t tick_count) {
    if (!sensor_b_has_value) {
        // Always return true if this is the first value
        sensor_b_has_value = true;
        sensor_b_level = level;
        settled_tick_b = tick_count;
        return true;
    }
    if (sensor_b_level == level) {
        // If the value has not changed, update the settled tick "time"
        settled_tick_b = tick_count;
        return false;
    }
    if (tick_count - settled_tick_b < debounce_threshold) {
        // If the value has changed, but not for long enough, ignore it
        return false;
    }
    // Return true if the value has changed
    sensor_b_level = level;
    settled_tick_b = tick_count;
    return true;
}
