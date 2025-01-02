#include "garage_config.h"
#ifndef CONFIG_USE_FAKE_GARAGE_SERVER

#include "cJSON.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include <stdio.h>
#include <string.h>

#include "garage_server.h"

static const char *TAG = "garage_server";

// Set the server URLs with: idf.py menuconfig
#define GARAGE_SERVER_BASE_URL CONFIG_GARAGE_SERVER_BASE_URL // "https://example.com"
#define SENSOR_VALUES_ENDPOINT CONFIG_SENSOR_VALUES_ENDPOINT // "/sensor_values"
#define BUTTON_TOKEN_ENDPOINT CONFIG_BUTTON_TOKEN_ENDPOINT   // "/button_token"
#define SENSOR_VALUES_URL GARAGE_SERVER_BASE_URL SENSOR_VALUES_ENDPOINT
#define BUTTON_TOKEN_URL GARAGE_SERVER_BASE_URL BUTTON_TOKEN_ENDPOINT

#define HTTP_RECEIVE_BUFFER_SIZE 1024

extern const uint8_t server_root_cert_pem_start[] asm("_binary_server_root_cert_pem_start");
extern const uint8_t server_root_cert_pem_end[] asm("_binary_server_root_cert_pem_end");

typedef struct {
    char *buffer;
    size_t buffer_len;
    size_t data_received_len;
} http_receive_buffer_t;

static void reset_http_buffer(http_receive_buffer_t *buffer) {
    if (buffer && buffer->buffer) {
        memset(buffer->buffer, 0, buffer->buffer_len);
        buffer->data_received_len = 0;
    }
}

esp_err_t _http_button_token_event_handler(esp_http_client_event_t *evt) {
    static http_receive_buffer_t recv_buffer = {0};

    switch (evt->event_id) {
    case HTTP_EVENT_ERROR:
        ESP_LOGE(TAG, "HTTP_EVENT_ERROR");
        break;

    case HTTP_EVENT_ON_CONNECTED:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_CONNECTED");
        // Allocate buffer on connect
        if (recv_buffer.buffer == NULL) {
            recv_buffer.buffer = (char *)malloc(HTTP_RECEIVE_BUFFER_SIZE);
            if (recv_buffer.buffer == NULL) {
                ESP_LOGE(TAG, "Failed to allocate memory for HTTP receive buffer");
                return ESP_FAIL;
            }
            recv_buffer.buffer_len = HTTP_RECEIVE_BUFFER_SIZE;
            reset_http_buffer(&recv_buffer); // Initialize the buffer
        }
        break;

    case HTTP_EVENT_ON_HEADER:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_HEADER, key=%s, value=%s", evt->header_key, evt->header_value);
        break;

    case HTTP_EVENT_ON_DATA:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_DATA, len=%d", evt->data_len);

        // Check for buffer overflow
        if (recv_buffer.data_received_len + evt->data_len > recv_buffer.buffer_len) {
            ESP_LOGE(TAG, "HTTP receive buffer overflow");
            return ESP_FAIL;
        }

        // Copy the new data into the buffer
        memcpy(recv_buffer.buffer + recv_buffer.data_received_len, evt->data, evt->data_len);
        recv_buffer.data_received_len += evt->data_len;
        ESP_LOGI(TAG, "Current buffer content: %.*s", recv_buffer.data_received_len, recv_buffer.buffer);
        break;

    case HTTP_EVENT_ON_FINISH:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_FINISH");
        // Parse the JSON data after all data is received
        if (recv_buffer.buffer != NULL && recv_buffer.data_received_len > 0) {
            cJSON *root = cJSON_ParseWithLength(recv_buffer.buffer, recv_buffer.data_received_len);
            if (root == NULL) {
                ESP_LOGE(TAG, "Failed to parse JSON");
            } else {
                // Process JSON data
                ESP_LOGI(TAG, "Parsed JSON: %s", cJSON_Print(root));
                cJSON_Delete(root);
            }
            reset_http_buffer(&recv_buffer);
        }
        break;

    case HTTP_EVENT_DISCONNECTED:
        ESP_LOGI(TAG, "HTTP_EVENT_DISCONNECTED");
        // Clean up on disconnect
        if (recv_buffer.buffer != NULL) {
            free(recv_buffer.buffer);
            recv_buffer.buffer = NULL;
            recv_buffer.buffer_len = 0;
            recv_buffer.data_received_len = 0;
        }
        break;

    default:
        break;
    }
    return ESP_OK;
}

static esp_err_t https_button_token_post_request(const char *url, const char *post_data, int post_data_len) {
    esp_http_client_config_t config = {
        .url = url,
        .event_handler = _http_button_token_event_handler,
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


esp_err_t _http_sensor_values_event_handler(esp_http_client_event_t *evt) {
    static http_receive_buffer_t recv_buffer = {0};

    switch (evt->event_id) {
    case HTTP_EVENT_ERROR:
        ESP_LOGE(TAG, "HTTP_EVENT_ERROR");
        break;

    case HTTP_EVENT_ON_CONNECTED:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_CONNECTED");
        // Allocate buffer on connect
        if (recv_buffer.buffer == NULL) {
            recv_buffer.buffer = (char *)malloc(HTTP_RECEIVE_BUFFER_SIZE);
            if (recv_buffer.buffer == NULL) {
                ESP_LOGE(TAG, "Failed to allocate memory for HTTP receive buffer");
                return ESP_FAIL;
            }
            recv_buffer.buffer_len = HTTP_RECEIVE_BUFFER_SIZE;
            reset_http_buffer(&recv_buffer); // Initialize the buffer
        }
        break;

    case HTTP_EVENT_ON_HEADER:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_HEADER, key=%s, value=%s", evt->header_key, evt->header_value);
        break;

    case HTTP_EVENT_ON_DATA:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_DATA, len=%d", evt->data_len);

        // Check for buffer overflow
        if (recv_buffer.data_received_len + evt->data_len > recv_buffer.buffer_len) {
            ESP_LOGE(TAG, "HTTP receive buffer overflow");
            return ESP_FAIL;
        }

        // Copy the new data into the buffer
        memcpy(recv_buffer.buffer + recv_buffer.data_received_len, evt->data, evt->data_len);
        recv_buffer.data_received_len += evt->data_len;
        ESP_LOGI(TAG, "Current buffer content: %.*s", recv_buffer.data_received_len, recv_buffer.buffer);
        break;

    case HTTP_EVENT_ON_FINISH:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_FINISH");
        // Parse the JSON data after all data is received
        if (recv_buffer.buffer != NULL && recv_buffer.data_received_len > 0) {
            cJSON *root = cJSON_ParseWithLength(recv_buffer.buffer, recv_buffer.data_received_len);
            if (root == NULL) {
                ESP_LOGE(TAG, "Failed to parse JSON");
            } else {
                // Process JSON data
                ESP_LOGI(TAG, "Parsed JSON: %s", cJSON_Print(root));
                cJSON_Delete(root);
            }
            reset_http_buffer(&recv_buffer);
        }
        break;

    case HTTP_EVENT_DISCONNECTED:
        ESP_LOGI(TAG, "HTTP_EVENT_DISCONNECTED");
        // Clean up on disconnect
        if (recv_buffer.buffer != NULL) {
            free(recv_buffer.buffer);
            recv_buffer.buffer = NULL;
            recv_buffer.buffer_len = 0;
            recv_buffer.data_received_len = 0;
        }
        break;

    default:
        break;
    }
    return ESP_OK;
}

static esp_err_t https_sensor_values_post_request(const char *url, const char *post_data, int post_data_len) {
    esp_http_client_config_t config = {
        .url = url,
        .event_handler = _http_sensor_values_event_handler,
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

void real_garage_server_init(void) {
    ESP_LOGI(TAG, "Initialize garage server");
    ESP_LOGI(TAG, "Server root certificate: %s", server_root_cert_pem_start);
}

void real_garage_server_send_sensor_values(sensor_request_t *sensor_request, sensor_response_t *sensor_response) {
    ESP_LOGI(TAG, "Send sensor values to server: device_id: %s, sensor_a: %d, sensor_b: %d",
             sensor_request->device_id,
             sensor_request->sensor_a,
             sensor_request->sensor_b);
    // sensorA: 0 (door closed), 1 (door not closed)
    // sensorB: 0 (door open), 1 (door not open)

    // 1. Create JSON Payload:
    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "device_id", sensor_request->device_id);
    cJSON_AddNumberToObject(root, "sensor_a", sensor_request->sensor_a);
    cJSON_AddNumberToObject(root, "sensor_b", sensor_request->sensor_b);

    char *json_payload = cJSON_Print(root);
    cJSON_Delete(root);

    if (json_payload == NULL) {
        ESP_LOGE(TAG, "Failed to create JSON payload");
        return; // Handle the error appropriately
    }

    // 2. Construct URL with Parameters:
    char url_with_params[512];
    // This is a legacy URL pattern for an old version of the server.
    // The legacy URL path is /echo (a generic endpoint), which needs to be updated in idf.py menuconfig
    // URL query parameters: ?buildTimestamp=${device_id}&sensorA=${sensor_a}&sensorB=${sensor_b}
    snprintf(url_with_params, sizeof(url_with_params),
             "%s?buildTimestamp=%s&sensorA=%d&sensorB=%d",
             SENSOR_VALUES_URL, sensor_request->device_id, sensor_request->sensor_a, sensor_request->sensor_b);

    ESP_LOGI(TAG, "URL with parameters: %s", url_with_params);
    // 3. Send HTTPS POST Request:
    esp_err_t err = https_sensor_values_post_request(url_with_params, json_payload, strlen(json_payload));

    // 4. Handle Response (if needed):
    if (err == ESP_OK) {
        ESP_LOGI(TAG, "Sensor values sent");

        // ... (Response handling code - see previous example) ...

    } else {
        ESP_LOGE(TAG, "Failed to send sensor values");
    }

    // 5. Free memory allocated for JSON payload:
    free(json_payload);
}

void real_garage_server_send_button_token(button_request_t *button_request, button_response_t *button_response) {
    ESP_LOGI(TAG, "Send button token to server: device_id: %s, button_token: %s",
             button_request->device_id,
             button_request->button_token);

    // 1. Create JSON Payload:
    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "device_id", button_request->device_id);
    cJSON_AddStringToObject(root, "button_token", button_request->button_token);

    char *json_payload = cJSON_Print(root);
    cJSON_Delete(root);

    if (json_payload == NULL) {
        ESP_LOGE(TAG, "Failed to create JSON payload");
        return; // Handle the error appropriately
    }

    // 2. Construct URL with Parameters:
    char url_with_params[1024];

    // URL query parameters: ?buildTimestamp=${device_id}&buttonAckToken=${button_token}
    snprintf(url_with_params, sizeof(url_with_params),
             "%s?buildTimestamp=%s&buttonAckToken=%s",
             BUTTON_TOKEN_URL, button_request->device_id, button_request->button_token);

    ESP_LOGI(TAG, "URL with parameters: %s", url_with_params);

    // 3. Send HTTPS POST Request:
    esp_err_t err = https_button_token_post_request(url_with_params, json_payload, strlen(json_payload));

    // 4. Handle Response (if needed):
    if (err == ESP_OK) {
        ESP_LOGI(TAG, "Button token sent successfully");

        // ... (Response handling code - same as in send_sensor_values) ...
    } else {
        ESP_LOGE(TAG, "Failed to send button token");
    }

    // 5. Free memory allocated for JSON payload:
    free(json_payload);
}

extern garage_server_t garage_server = {
    .init = real_garage_server_init,
    .send_sensor_values = real_garage_server_send_sensor_values,
    .send_button_token = real_garage_server_send_button_token,
};

#endif // CONFIG_USE_FAKE_GARAGE_SERVER
