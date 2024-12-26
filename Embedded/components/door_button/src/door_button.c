#include "esp_log.h"
#include <stdio.h>
#include <string.h>

#include "door_button.h"

#define TAG "door_button.c"

static char button_token[MAX_BUTTON_TOKEN_LENGTH + 1] = "";

void button_init(void) {
    button_token[0] = '\0';
}

bool should_push_button(const char *new_button_token) {
    if (strcmp(button_token, new_button_token) == 0) {
        ESP_LOGI(TAG, "Button token is not changed");
        return false;
    } else if (button_token[0] == '\0') {
        // Important: Do not push the button if this is the first token.
        // This is to prevent the button from being pushed when the device is first powered on.
        ESP_LOGI(TAG, "Not pushing button because %s is the first token", new_button_token);
        strncpy(button_token, new_button_token, MAX_BUTTON_TOKEN_LENGTH);
        ESP_LOGI(TAG, "Button token is now %s", button_token);
        return false;
    } else {
        ESP_LOGI(TAG, "Push the button for %s", new_button_token);
        strncpy(button_token, new_button_token, MAX_BUTTON_TOKEN_LENGTH);
        ESP_LOGI(TAG, "Button token is now %s", button_token);
        return true;
    }
}

char* get_button_token(void) {
    return button_token;
}
