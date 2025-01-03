#ifndef ROOT_CA_H
#define ROOT_CA_H

#include "garage_config.h"
#include <stdint.h>

// IDF uses the EMBED_TXTFILES directive to include the root CA certificate.
// server_root_cert_info.txt
//     Human readable information about the certificate showing expiration in 2036.
// server_root_cert_info.pem:
//     EMBED_TXTFILES
//         "server_root_cert.pem"
extern const uint8_t server_root_cert_pem_start[] asm("_binary_server_root_cert_pem_start");
extern const uint8_t server_root_cert_pem_end[] asm("_binary_server_root_cert_pem_end");

#endif // ROOT_CA_H
