#ifndef ROOT_CA_H
#define ROOT_CA_H

#include "garage_config.h"
#include <stdint.h>

extern const uint8_t server_root_cert_pem_start[] asm("_binary_server_root_cert_pem_start");
extern const uint8_t server_root_cert_pem_end[] asm("_binary_server_root_cert_pem_end");

#endif // ROOT_CA_H
