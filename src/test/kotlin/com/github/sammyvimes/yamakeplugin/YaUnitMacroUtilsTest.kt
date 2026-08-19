package com.github.sammyvimes.yamakeplugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YaUnitMacroUtilsTest {
    @Test
    fun `finds full unit test name without Classic C++ PSI`() {
        val source = """
            Y_UNIT_TEST_SUITE(TPDiskTest) {
                Y_UNIT_TEST(TestPDiskActorErrorState) {
                }
            }
        """.trimIndent()
        val offset = source.indexOf("Y_UNIT_TEST(Test")

        val call = findYaUnitTestCall(source, offset)

        assertEquals("TestPDiskActorErrorState", call?.testName)
        assertEquals("TPDiskTest::TestPDiskActorErrorState", call?.fullName)
    }

    @Test
    fun `does not match a different offset`() {
        val source = "Y_UNIT_TEST(TestOne) {}"

        assertNull(findYaUnitTestCall(source, source.indexOf("TestOne")))
    }
}
