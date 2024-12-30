#ifndef CONFIG_USE_FAKE_GARAGE_SERVER

#include "cJSON.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include <stdio.h>
#include <string.h>

#include "real_garage_server.h"

static const char *TAG = "real_garage_server";

#define TEST_URL "https://us-central1-escape-echo.cloudfunctions.net/echo"
#define GARAGE_SERVER_BASE_URL CONFIG_GARAGE_SERVER_BASE_URL // "https://example.com"
#define SENSOR_VALUES_ENDPOINT CONFIG_SENSOR_VALUES_ENDPOINT // "/sensor_values"
#define BUTTON_TOKEN_ENDPOINT CONFIG_BUTTON_TOKEN_ENDPOINT   // "/button_token"
#define SENSOR_VALUES_URL GARAGE_SERVER_BASE_URL SENSOR_VALUES_ENDPOINT
#define BUTTON_TOKEN_URL GARAGE_SERVER_BASE_URL BUTTON_TOKEN_ENDPOINT

extern const uint8_t server_root_cert_pem_start[] asm("_binary_server_root_cert_pem_start");
extern const uint8_t server_root_cert_pem_end[] asm("_binary_server_root_cert_pem_end");

esp_err_t _http_event_handler(esp_http_client_event_t *evt) {
    switch (evt->event_id) {
    case HTTP_EVENT_ERROR:
        ESP_LOGE(TAG, "HTTP_EVENT_ERROR");
        break;
    case HTTP_EVENT_ON_DATA:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_DATA, len=%d", evt->data_len);
        // Handle the response data here.
        // Example: If you expect a JSON response, you can parse it using cJSON:

        if (!esp_http_client_is_chunked_response(evt->client)) {
            cJSON *root = cJSON_ParseWithLength((char *)evt->data, evt->data_len);
            if (root == NULL) {
                ESP_LOGE(TAG, "Failed to parse JSON");
            } else {
                // Process JSON data
                ESP_LOGI(TAG, "Parsed JSON: %s", cJSON_Print(root));
                cJSON_Delete(root);
            }
        }

        break;
    case HTTP_EVENT_ON_FINISH:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_FINISH");
        break;
    default:
        break;
    }
    return ESP_OK;
}

static esp_err_t https_post_request(const char *url, const char *post_data, int post_data_len) {
    esp_http_client_config_t config = {
        .url = url,
        .event_handler = _http_event_handler,
        .cert_pem = (const char *)server_root_cert_pem_start,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);

    esp_http_client_set_method(client, HTTP_METHOD_POST);
    esp_http_client_set_header(client, "Content-Type", "application/json");
    esp_http_client_set_post_field(client, post_data, post_data_len);

    esp_err_t err = esp_http_client_perform(client);
    if (err == ESP_OK) {
        ESP_LOGI(TAG, "HTTPS POST Status = %d, content_length = %" PRId64,
                 esp_http_client_get_status_code(client),
                 esp_http_client_get_content_length(client));
    } else {
        ESP_LOGE(TAG, "HTTPS POST request failed: %s", esp_err_to_name(err));
    }

    esp_http_client_cleanup(client);
    return err;
}

extern garage_server_t garage_server = {
    .init = real_garage_server_init,
    .send_sensor_values = real_garage_server_send_sensor_values,
    .send_button_token = real_garage_server_send_button_token,
};

void real_garage_server_init(void) {
    ESP_LOGI(TAG, "Initialize garage server");
    ESP_LOGI(TAG, "Server root certificate: %s", server_root_cert_pem_start);

    esp_err_t err = https_post_request(TEST_URL, "{}", 2);
    if (err == ESP_OK) {
        ESP_LOGI(TAG, "HTTPS POST request successful (test)");
    } else {
        ESP_LOGE(TAG, "HTTPS POST request failed (test)");
    }
}

void real_garage_server_send_sensor_values(sensor_request_t *sensor_request, sensor_response_t *sensor_response) {
    ESP_LOGI(TAG, "TODO: Send sensor values to server: device_id: %s, sensor_a: %d, sensor_b: %d",
             sensor_request->device_id,
             sensor_request->sensor_a,
             sensor_request->sensor_b);
}

void real_garage_server_send_button_token(button_request_t *button_request, button_response_t *button_response) {
    ESP_LOGI(TAG, "TODO: Send button token to server: device_id: %s, button_token: %s",
             button_request->device_id,
             button_request->button_token);
}

#endif // CONFIG_USE_FAKE_GARAGE_SERVER
