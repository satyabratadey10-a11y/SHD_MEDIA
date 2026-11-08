package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun username_sanitization_removesLeadingAtAndWhitespace() {
        val input = "  @satyabrata  "
        val clean = input.trim().removePrefix("@").trim()
        assertEquals("satyabrata", clean)
    }

    @Test
    fun deterministicConversationId_isConsistent() {
        val uid1 = "user_abc"
        val uid2 = "user_xyz"
        val sorted1 = listOf(uid1, uid2).sorted()
        val convId1 = "dm_${sorted1[0]}_${sorted1[1]}"

        val sorted2 = listOf(uid2, uid1).sorted()
        val convId2 = "dm_${sorted2[0]}_${sorted2[1]}"

        assertEquals(convId1, convId2)
        assertEquals("dm_user_abc_user_xyz", convId1)
    }
}
