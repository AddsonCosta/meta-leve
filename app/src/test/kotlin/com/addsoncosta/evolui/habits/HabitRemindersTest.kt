package com.addsoncosta.evolui.habits

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitRemindersTest {
    @Test
    fun slotsSpreadFromStartToEnd() {
        assertEquals(listOf(420, 720, 1020), HabitReminders.slots(420, 1020, 3))
        assertEquals(listOf(480), HabitReminders.slots(480, 1200, 1))
        assertEquals(emptyList<Int>(), HabitReminders.slots(480, 1200, 0))
    }

    @Test
    fun waterCountIsOneGlassPerReminder() {
        assertEquals(8, HabitReminders.waterCount(2000.0))
        assertEquals(4, HabitReminders.waterCount(500.0))
        assertEquals(16, HabitReminders.waterCount(6000.0))
    }

    @Test
    fun nextTriggerGoesToTomorrowAfterLastSlot() {
        val now = LocalDateTime.of(2026, 11, 3, 21, 0)
        assertEquals(LocalDateTime.of(2026, 11, 4, 7, 0), HabitReminders.nextTrigger(listOf(420, 1200), now))
        assertEquals(LocalDateTime.of(2026, 11, 3, 20, 0), HabitReminders.nextTrigger(listOf(420, 1200), now.withHour(10)))
    }
}
