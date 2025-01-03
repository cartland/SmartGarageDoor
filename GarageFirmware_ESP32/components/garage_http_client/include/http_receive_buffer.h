#ifndef HTTP_RECEIVE_BUFFER_H
#define HTTP_RECEIVE_BUFFER_H

#include <stddef.h>

typedef struct {
    char *buffer;
    size_t buffer_len;
    size_t data_received_len;
    int status_code;
} http_receive_buffer_t;

void reset_http_buffer(http_receive_buffer_t *buffer);

#endif // HTTP_RECEIVE_BUFFER_H 
