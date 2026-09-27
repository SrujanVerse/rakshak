package com.rakshak.core.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorRingBufferTest {

    @Test
    fun testBufferEvictsBasedOnTimeWindow() {
        // 4 seconds in nanos
        val buffer = SensorRingBuffer(timeWindowNanos = 4_000_000_000L, maxCapacity = 10)
        
        // Add item at 0 nanos
        buffer.add(0L, floatArrayOf(1f))
        
        // Add item at 2 seconds
        buffer.add(2_000_000_000L, floatArrayOf(2f))
        
        // Add item at 4.1 seconds - should evict the first item (0L)
        buffer.add(4_100_000_000L, floatArrayOf(3f))

        val snapshot = buffer.flow.value
        assertEquals(2, snapshot.size)
        assertEquals(2_000_000_000L, snapshot[0].timestamp)
        assertEquals(4_100_000_000L, snapshot[1].timestamp)
    }
    
    @Test
    fun testBufferNeverExceedsAbsoluteMaxCapacity() {
        // Time window is large, but max capacity is 5
        val buffer = SensorRingBuffer(timeWindowNanos = 10_000_000_000L, maxCapacity = 5)
        
        // Add 10 items very quickly (within time window)
        for (i in 1..10) {
            buffer.add(i.toLong() * 1000L, floatArrayOf(i.toFloat()))
        }

        // Buffer size should be capped at 5 due to absolute bound
        assertEquals(5, buffer.getCurrentSize())
        
        // Should contain items 6 to 10
        val snapshot = buffer.flow.value
        assertEquals(6000L, snapshot[0].timestamp)
        assertEquals(10000L, snapshot[4].timestamp)
    }

    @Test
    fun testCorrectMostRecentOrderIsPreserved() {
        val buffer = SensorRingBuffer(timeWindowNanos = 4_000_000_000L, maxCapacity = 5)
        
        for (i in 1..3) {
            buffer.add(i.toLong() * 1_000_000_000L, floatArrayOf(i.toFloat()))
        }

        val snapshot = buffer.flow.value
        
        assertEquals(3, snapshot.size)
        assertEquals(1_000_000_000L, snapshot[0].timestamp)
        assertEquals(2_000_000_000L, snapshot[1].timestamp)
        assertEquals(3_000_000_000L, snapshot[2].timestamp)
    }
}
