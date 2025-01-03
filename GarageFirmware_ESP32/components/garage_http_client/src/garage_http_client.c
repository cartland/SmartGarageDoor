#include "garage_config.h"
#ifndef CONFIG_USE_FAKE_GARAGE_SERVER

#include "cJSON.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include <stdio.h>
#include <string.h>

#include "garage_http_client.h"
#include "https_post_request.h"
#include "root_ca.h"

static const char *TAG = "garage_server";

// Set the server URLs with: idf.py menuconfig
#define GARAGE_SERVER_BASE_URL CONFIG_GARAGE_SERVER_BASE_URL // "https://example.com"
#define SENSOR_VALUES_ENDPOINT CONFIG_SENSOR_VALUES_ENDPOINT // "/sensor_values"
#define BUTTON_TOKEN_ENDPOINT CONFIG_BUTTON_TOKEN_ENDPOINT   // "/button_token"
#define SENSOR_VALUES_URL GARAGE_SERVER_BASE_URL SENSOR_VALUES_ENDPOINT
#define BUTTON_TOKEN_URL GARAGE_SERVER_BASE_URL BUTTON_TOKEN_ENDPOINT

void real_garage_server_init(void) {
    ESP_LOGI(TAG, "Initialize garage server");
    ESP_LOGI(TAG, "Server root certificate: %s", server_root_cert_pem_start);
}

void real_garage_server_send_sensor_values(sensor_request_t *sensor_request, sensor_response_t *sensor_response, http_receive_buffer_t *recv_buffer) {
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
    esp_err_t err = https_send_json_post_request(url_with_params, json_payload, strlen(json_payload), recv_buffer);

    // 4. Handle Response:
    if (err == ESP_OK) {
        if (recv_buffer->data_received_len > 0) {
            cJSON *root = cJSON_ParseWithLength(recv_buffer->buffer, recv_buffer->data_received_len);
            if (root == NULL) {
                ESP_LOGE(TAG, "Failed to parse JSON");
            } else {
                if (recv_buffer->status_code == 200) {
                    ESP_LOGI(TAG, "Button token sent successfully (200)");
                } else {
                    ESP_LOGE(TAG, "Button token sent successfully, but server returned status code %d", recv_buffer->status_code);
                }
                ESP_LOGI(TAG, "Parsed JSON: %s", cJSON_Print(root));

                // Extract sensor values from the "body" object
                cJSON *body = cJSON_GetObjectItemCaseSensitive(root, "queryParams");
                if (cJSON_IsObject(body)) {
                    cJSON *device_id_json = cJSON_GetObjectItemCaseSensitive(body, "buildTimestamp");
                    cJSON *sensor_a_json = cJSON_GetObjectItemCaseSensitive(body, "sensorA");
                    cJSON *sensor_b_json = cJSON_GetObjectItemCaseSensitive(body, "sensorB");

                    if (cJSON_IsString(device_id_json) && (device_id_json->valuestring != NULL)) {
                        strncpy(sensor_response->device_id, device_id_json->valuestring, MAX_DEVICE_ID_LENGTH);
                        sensor_response->device_id[MAX_DEVICE_ID_LENGTH] = '\0';
                    }

                    if (cJSON_IsNumber(sensor_a_json)) {
                        sensor_response->sensor_a = sensor_a_json->valueint;
                    }

                    if (cJSON_IsNumber(sensor_b_json)) {
                        sensor_response->sensor_b = sensor_b_json->valueint;
                    }
                }

                cJSON_Delete(root);
            }
        }
    } else {
        ESP_LOGE(TAG, "Failed to send button token");
    }

    // 5. Free memory allocated for JSON payload:
    free(json_payload);
}

void real_garage_server_send_button_token(button_request_t *button_request, button_response_t *button_response, http_receive_buffer_t *recv_buffer) {
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
    esp_err_t err = https_send_json_post_request(url_with_params, json_payload, strlen(json_payload), recv_buffer);

    // 4. Handle Response:
    if (err == ESP_OK) {
        if (recv_buffer->data_received_len > 0) {
            cJSON *root = cJSON_ParseWithLength(recv_buffer->buffer, recv_buffer->data_received_len);
            if (root == NULL) {
                ESP_LOGE(TAG, "Failed to parse JSON");
            } else {
                if (recv_buffer->status_code == 200) {
                    ESP_LOGI(TAG, "Button token sent successfully (200)");
                } else {
                    ESP_LOGE(TAG, "Button token sent successfully, but server returned status code %d", recv_buffer->status_code);
                }
                ESP_LOGI(TAG, "Parsed JSON: %s", cJSON_Print(root));
                cJSON_Delete(root);
            }
        }
    } else {
        ESP_LOGE(TAG, "Failed to send button token");
    }

    // 5. Free memory allocated for JSON payload:
    free(json_payload);
}

garage_server_t garage_server = {
    .init = real_garage_server_init,
    .send_sensor_values = real_garage_server_send_sensor_values,
    .send_button_token = real_garage_server_send_button_token,
};

#endif // CONFIG_USE_FAKE_GARAGE_SERVER
