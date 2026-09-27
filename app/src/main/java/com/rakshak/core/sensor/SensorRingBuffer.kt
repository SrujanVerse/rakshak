package com.rakshak.core.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

data class SensorData(
    val timestamp: Long,
    val values: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SensorData
        if (timestamp != other.timestamp) return false
        if (!values.contentEquals(other.values)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }
}

/**
 * Thread-safe ring buffer for sensor events based on a rolling time window.
 * 
 * @param timeWindowNanos The rolling time window to retain (e.g., 4 seconds in nanoseconds).
 * @param maxCapacity An absolute maximum size to prevent memory bounds issues if sensors fire too rapidly.
 */
class SensorRingBuffer(
    private val timeWindowNanos: Long = 4_000_000_000L,
    private val maxCapacity: Int = 1000
) {
    private val buffer = ArrayDeque<SensorData>(maxCapacity)
    
    private val _flow = MutableStateFlow<List<SensorData>>(emptyList())
    val flow: StateFlow<List<SensorData>> = _flow.asStateFlow()

    @Synchronized
    fun add(timestamp: Long, values: FloatArray) {
        buffer.addLast(SensorData(timestamp, values.clone()))
        
        // Evict older samples outside the time window
        while (buffer.isNotEmpty() && timestamp - buffer.first().timestamp > timeWindowNanos) {
            buffer.removeFirst()
        }
        
        // Safety bound: prevent unbounded growth
        while (buffer.size > maxCapacity) {
            buffer.removeFirst()
        }
        
        // Emit the current snapshot
        _flow.value = buffer.toList()
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _flow.value = emptyList()
    }
    
    @Synchronized
    fun getCurrentSize(): Int = buffer.size
}
