#ifndef DOOR_BUTTON_H
#define DOOR_BUTTON_H

#include <stdbool.h>
#include <stdint.h>

#include "garage_config.h"

void button_init(void);

bool should_push_button(const char *new_button_token);

char *get_button_token(void);

void save_button_token(const char *new_button_token);

#endif // DOOR_BUTTON_H
