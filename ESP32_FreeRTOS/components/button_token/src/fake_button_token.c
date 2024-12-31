#include "garage_config.h"
#ifdef CONFIG_USE_FAKE_BUTTON_TOKEN

#include "button_token.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include <stdio.h>
#include <string.h>

static const char *TAG = "button_token";

static bool request_button_press = false;

static void button_init(button_token_t *token) {
    (*token)[0] = '\0';
}

static bool is_button_press_requested(button_token_t *token, const char *new_token) {
    return request_button_press;
}

static void consume_button_token(button_token_t *token, const char *new_token) {
    // Alternate between requesting a button press and not requesting a button press
    request_button_press = !request_button_press;
    snprintf(*token, MAX_BUTTON_TOKEN_LENGTH, "%s", new_token);
    ESP_LOGI(TAG, "Button token is now %s", *token);
}

button_token_manager_t token_manager = {
    .init = button_init,
    .is_button_press_requested = is_button_press_requested,
    .consume_button_token = consume_button_token,
};

#endif // CONFIG_USE_FAKE_BUTTON_PRESSER
