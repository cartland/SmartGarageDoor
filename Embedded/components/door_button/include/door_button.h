#ifndef DOOR_BUTTON_H
#define DOOR_BUTTON_H

#include <stdbool.h>
#include <stdint.h>

#define MAX_BUTTON_TOKEN_LENGTH 256

void button_init(void);

bool should_push_button(const char *new_button_token);

char* get_button_token(void);

#endif // DOOR_BUTTON_H
