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
        ESP_LOGD(TAG, "Button token is not changed");
        return false;
    } else if (strlen(new_token) == 0) {
        ESP_LOGD(TAG, "Button press not requested because button token is empty");
        return false;
    } else {
        // Button token is sensitive — anyone with a UART connection (USB cable
        // to the dev board) can read INFO-level logs. Log at DEBUG so the
        // token only appears in builds that explicitly raise the log level
        // above the default INFO threshold. Security audit reference: C2.
        ESP_LOGD(TAG, "Push the button for %s", new_token);
        return true;
    }
}

static void consume_button_token(button_token_t *token, const char *new_token) {
    snprintf(*token, MAX_BUTTON_TOKEN_LENGTH, "%s", new_token);
    // Sensitive — see is_button_press_requested above.
    ESP_LOGD(TAG, "Button token is now %s", *token);
}

button_token_manager_t token_manager = {
    .init = button_init,
    .is_button_press_requested = is_button_press_requested,
    .consume_button_token = consume_button_token,
};

#endif // CONFIG_USE_FAKE_BUTTON_PRESSER
