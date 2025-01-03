#ifndef HTTP_BUTTON_REQUEST_H
#define HTTP_BUTTON_REQUEST_H

#include "esp_http_client.h"
#include "garage_server.h"

esp_err_t https_button_token_post_request(const char *url, const char *post_data, int post_data_len, http_receive_buffer_t *recv_buffer);

#endif // HTTP_BUTTON_REQUEST_H