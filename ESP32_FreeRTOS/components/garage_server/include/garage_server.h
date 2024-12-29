#ifndef GARAGE_SERVER_H
#define GARAGE_SERVER_H

#include "garage_config.h"
#include "garage_server_t.h"

#ifdef CONFIG_USE_FAKE_GARAGE_SERVER // Set in garage_config.h
#include "fake_garage_server.h"
#else
#include "real_garage_server.h"
#endif

extern garage_server_t garage_server;

#endif // GARAGE_SERVER_H
