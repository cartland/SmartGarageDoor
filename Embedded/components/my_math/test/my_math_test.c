#include "unity.h"
#include "my_math.h"

TEST_CASE("my_add", "[my_math]") {
    TEST_ASSERT_EQUAL_UINT32(5, my_add(2, 3));
    TEST_ASSERT_EQUAL_UINT32(5, my_add(0, 5));
    TEST_ASSERT_EQUAL_UINT32(7000000000, my_add(4000000000, 3000000000));
    TEST_ASSERT_EQUAL_UINT32(14, my_add(7, 7));
    TEST_ASSERT_EQUAL_UINT32(4294967295, my_add(4294967295, 0));
    TEST_ASSERT_EQUAL_UINT32(0, my_add(5, -5));
}
