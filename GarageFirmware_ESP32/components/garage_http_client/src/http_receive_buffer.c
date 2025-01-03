#include "http_receive_buffer.h"
#include <string.h>

void reset_http_buffer(http_receive_buffer_t *buffer) {
    if (buffer != NULL && buffer->buffer != NULL) {
        memset(buffer->buffer, 0, buffer->buffer_len);
        buffer->data_received_len = 0;
        buffer->status_code = 0;
    }
}
