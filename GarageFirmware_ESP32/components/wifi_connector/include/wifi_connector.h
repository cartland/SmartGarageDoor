#ifndef WIFI_CONNECTOR_H
#define WIFI_CONNECTOR_H

#include "esp_err.h"

/**
 * @brief Initializes the Wi-Fi driver and connects to the configured network.
 *
 * @return esp_err_t ESP_OK if connection is successful, otherwise an error code.
 */
esp_err_t wifi_connector_init(void);

/**
 * @brief Deinitializes the Wi-Fi driver and disconnects from the network.
 *
 * @return esp_err_t ESP_OK if disconnection is successful, otherwise an error code.
 */
esp_err_t wifi_connector_deinit(void);

/**
 * @brief Returns whether the Wi-Fi is currently connected.
 *
 * @return true if connected, false otherwise.
 */
bool wifi_connector_is_connected(void);

#endif // WIFI_CONNECTOR_H
