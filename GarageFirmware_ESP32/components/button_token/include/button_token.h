#ifndef DOOR_BUTTON_H
#define DOOR_BUTTON_H

#include <stdbool.h>
#include <stdint.h>

#include "garage_config.h"

/**
 * The purpose of the button token is to ensure that the button is pressed only when the client observes a "push button" request from the server.
 * To simplify the memory and timing requirements, we introduce the concept of a button token, which is changed every time the server wants the client to push a button.
 * The memory requirement is very simple -- just a small string of characters.
 * The timing is also simple -- the client should poll the server frequently to observe a change.
 *
 * init: Initialize the button token.
 * is_button_press_requested: Check if the button press is requested.
 * consume_button_token: Consume the button token.
 *
 * is_button_press_requested should return true if the new_button_token is different from the button_token,
 * but only if it is not the first button token after init(). This avoids pressing the button when the device is first powered on.
 */
typedef char button_token_t[MAX_BUTTON_TOKEN_LENGTH + 1];

typedef struct {
    void (*init)(button_token_t *button_token);
    bool (*is_button_press_requested)(button_token_t *button_token, const char *new_button_token);
    void (*consume_button_token)(button_token_t *button_token, const char *new_button_token);
} button_token_manager_t;

extern button_token_manager_t token_manager;

#endif // DOOR_BUTTON_H
