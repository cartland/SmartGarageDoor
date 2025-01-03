#include "garage_config.h"

#include "esp_http_client.h"
#include "esp_log.h"
#include <string.h>

#include "http_receive_buffer.h"
#include "https_post_request.h"
#include "root_ca.h"

static const char *TAG = "http_button_request";

/**
 * This function is the event handler for the HTTP client.
 * It is called when the HTTP client receives data from the server.
 * A single HTTP request can be made up of multiple events.
 *
 * Input:
 * evt->user_data is a pointer to the http_receive_buffer_t struct
 *   ->buffer must be allocated by the caller with length buffer_len
 *   ->buffer_len must be set by the caller
 *   ->data_received_len will be set to the length of the data received
 *
 * Output:
 * evt->user_data->buffer will be filled with the data received from the server
 * evt->user_data->data_received_len will be set to the length of the data received
 */
static esp_err_t _http_event_handler(esp_http_client_event_t *evt) {
    http_receive_buffer_t *recv_buffer = (http_receive_buffer_t *)evt->user_data;

    if (recv_buffer == NULL || recv_buffer->buffer == NULL || recv_buffer->buffer_len == 0) {
        ESP_LOGE(TAG, "->user_data is not configured for receiving data as http_receive_buffer_t");
        return ESP_FAIL;
    }

    switch (evt->event_id) {
    case HTTP_EVENT_ERROR:
        ESP_LOGE(TAG, "HTTP_EVENT_ERROR");
        break;

    case HTTP_EVENT_ON_CONNECTED:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_CONNECTED");
        if (recv_buffer->buffer == NULL) {
            ESP_LOGE(TAG, "HTTP_EVENT_ON_CONNECTED: buffer is NULL");
            return ESP_FAIL;
        }
        // Clear buffer before receiving data
        reset_http_buffer(recv_buffer);
        break;

    case HTTP_EVENT_ON_HEADER:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_HEADER, key=%s, value=%s", evt->header_key, evt->header_value);
        break;

    case HTTP_EVENT_ON_DATA:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_DATA, len=%d", evt->data_len);

        // Check for buffer overflow
        if (recv_buffer->data_received_len + evt->data_len > recv_buffer->buffer_len) {
            ESP_LOGE(TAG, "HTTP receive buffer overflow");
            return ESP_FAIL;
        }

        // Copy the new data into the buffer
        memcpy(recv_buffer->buffer + recv_buffer->data_received_len, evt->data, evt->data_len);
        recv_buffer->data_received_len += evt->data_len;
        ESP_LOGI(TAG, "Current buffer content: %.*s", recv_buffer->data_received_len, recv_buffer->buffer);
        break;

    case HTTP_EVENT_ON_FINISH:
        ESP_LOGI(TAG, "HTTP_EVENT_ON_FINISH received: %d bytes", recv_buffer->data_received_len);
        break;

    case HTTP_EVENT_DISCONNECTED:
        ESP_LOGI(TAG, "HTTP_EVENT_DISCONNECTED");
        break;

    default:
        break;
    }
    return ESP_OK;
}

/**
 * Send a POST request to the given URL with the given data.
 * The data is sent as JSON.
 * The response is received in the recv_buffer.
 * The status code is returned in recv_buffer->status_code.
 * The data received is returned in recv_buffer->buffer.
 * The length of the data received is returned in recv_buffer->data_received_len.
 *
 * Returns ESP_OK if the request is successful, otherwise returns ESP_FAIL.
 */
esp_err_t https_send_json_post_request(const char *url, const char *post_data, int post_data_len, http_receive_buffer_t *recv_buffer) {
    esp_http_client_config_t config = {
        .url = url,
        .event_handler = _http_event_handler,
        .cert_pem = (const char *)server_root_cert_pem_start,
        .user_data = recv_buffer,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);

    esp_http_client_set_method(client, HTTP_METHOD_POST);
    esp_http_client_set_header(client, "Content-Type", "application/json");
    esp_http_client_set_post_field(client, post_data, post_data_len);

    esp_err_t err = esp_http_client_perform(client);
    if (err == ESP_OK) {
        int status_code = esp_http_client_get_status_code(client);
        recv_buffer->status_code = status_code;
        int64_t content_length = esp_http_client_get_content_length(client);
        ESP_LOGI(TAG, "HTTPS POST Status = %d, content_length = %" PRId64,
                 status_code,
                 content_length);

        if (content_length != recv_buffer->data_received_len) {
            ESP_LOGW(TAG, "HTTPS POST request received %d bytes, but expected %" PRId64,
                     recv_buffer->data_received_len,
                     content_length);
        }
    } else {
        ESP_LOGE(TAG, "HTTPS POST request failed: %s", esp_err_to_name(err));
    }

    esp_http_client_cleanup(client);
    return err;
}
