idf_component_register(SRCS "main.c"
                       INCLUDE_DIRS "."
                       REQUIRES door_button door_sensors garage_server my_hal)
