package com.arny.mobilecinema.data.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ArraysUtilsTest {
    private fun generateList(size: Int, isA: Boolean): List<User> {
        val list = mutableListOf<User>()
        for (i in 0 until size) {
            if (isA) {
                list.add(UserA(i, "name:${i + 1}"))
            } else {
                list.add(UserB(i, "surname:${i + 1}"))
            }
        }
        return list
    }

    private fun diffByGroup(list1: List<User>, list2: List<User>): List<User> {
        return (list1 + list2).groupBy { it.id }.filter { it.value.size == 1 }.flatMap { it.value }
    }

    @Test
    fun `test arrayDiff`() {
        val size1 = 1000000
        val size2 = size1 + 2
        val list1 = generateList(size1, true)
        val list2 = generateList(size2, false)
        val start = System.currentTimeMillis()

        val diff = diffByGroup(list1, list2)

//        println("diff time:${System.currentTimeMillis() - start}")
//        println("diff:$diff")
        val subList = list2.subList(size1, size2)
        assertEquals(
            /* expected = */ subList,
            /* actual = */ diff
        )
    }
}
