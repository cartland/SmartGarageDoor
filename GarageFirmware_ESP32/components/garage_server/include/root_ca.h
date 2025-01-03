#include "garage_config.h"
#include <stdint.h>
#ifndef CONFIG_USE_FAKE_GARAGE_SERVER

extern const uint8_t server_root_cert_pem_start[] asm("_binary_server_root_cert_pem_start");
extern const uint8_t server_root_cert_pem_end[] asm("_binary_server_root_cert_pem_end");

#endif // CONFIG_USE_FAKE_GARAGE_SERVER
