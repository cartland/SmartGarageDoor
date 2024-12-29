#ifndef DOOR_BUTTON_H
#define DOOR_BUTTON_H

#include <stdbool.h>
#include <stdint.h>

#include "garage_config.h"

typedef char button_token_t[MAX_BUTTON_TOKEN_LENGTH + 1];

typedef struct {
    void (*init)(button_token_t *button_token);
    bool (*is_button_press_requested)(button_token_t *button_token, const char *new_button_token);
    void (*consume_button_token)(button_token_t *button_token, const char *new_button_token);
} button_token_manager_t;

extern button_token_manager_t token_manager;

#endif // DOOR_BUTTON_H
