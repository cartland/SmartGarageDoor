#include "garage_config.h"
#ifndef CONFIG_USE_FAKE_BUTTON_TOKEN

#include "button_token.h"
#include <stdio.h>
#include <string.h>
#include "esp_log.h"

static const char *TAG = "button_token";

static void button_init(button_token_t *token) {
    strncpy(*token, "NO_BUTTON_TOKEN", MAX_BUTTON_TOKEN_LENGTH);
    (*token)[MAX_BUTTON_TOKEN_LENGTH] = '\0';
}

static bool is_button_press_requested(button_token_t *token, const char *new_token) {
    if (strcmp(*token, new_token) == 0) {
        ESP_LOGI(TAG, "Button token is not changed");
        return false;
    } else if ((*token)[0] == '\0') {
        // Important: Do not push the button if this is the first token.
        // This is to prevent the button from being pushed when the device is first powered on.
        ESP_LOGI(TAG, "Not pushing button because %s is the first token", new_token);
        return false;
    } else {
        ESP_LOGI(TAG, "Push the button for %s", new_token);
        return true;
    }
}

static void consume_button_token(button_token_t *token, const char *new_token) {
    snprintf(*token, MAX_BUTTON_TOKEN_LENGTH, "%s", new_token);
    ESP_LOGI(TAG, "Button token is now %s", *token);
}

button_token_manager_t token_manager = {
    .init = button_init,
    .is_button_press_requested = is_button_press_requested,
    .consume_button_token = consume_button_token,
};

#endif // CONFIG_USE_FAKE_BUTTON_PRESSER
