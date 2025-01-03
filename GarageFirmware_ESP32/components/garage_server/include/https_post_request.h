#ifndef HTTPS_POST_REQUEST_H
#define HTTPS_POST_REQUEST_H

#include "http_receive_buffer.h"

esp_err_t https_send_json_post_request(const char *url, const char *post_data, int post_data_len, http_receive_buffer_t *recv_buffer);

#endif // HTTPS_POST_REQUEST_H
