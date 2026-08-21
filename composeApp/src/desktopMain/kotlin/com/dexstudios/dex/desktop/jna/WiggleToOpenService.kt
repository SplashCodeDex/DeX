package com.dexstudios.dex.desktop.jna

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.POINT
import kotlinx.coroutines.*
import kotlin.math.abs

object WiggleToOpenService {
    private var job: Job? = null

    private data class MouseSample(
        val timeMs: Long,
        val x: Int,
        val y: Int,
        val isPrimaryDown: Boolean,
        val fgWindowX: Int,
        val fgWindowY: Int
    )
    
    fun start(deviceConfig: com.dexstudios.dex.core.network.DeviceConfig, onWake: (() -> Unit)? = null, onTrigger: () -> Unit) {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            println("WiggleToOpenService: Not on Windows. Skipping JNA mouse hook.")
            return
        }
        
        job = CoroutineScope(Dispatchers.IO).launch {
            val bufferSize = 60 // 60 samples @ 15ms = 900ms of history
            val buffer = Array(bufferSize) { MouseSample(0L, 0, 0, false, 0, 0) }
            var index = 0
            
            // SM_SWAPBUTTON (23). If non-zero, mouse buttons are swapped (left-handed mode).
            fun getPrimaryButtonCode(): Int {
                return if (User32.INSTANCE.GetSystemMetrics(23) != 0) 0x02 else 0x01
            }
            
            while (isActive) {
                val loopStartTime = System.currentTimeMillis()
                
                if (deviceConfig.wiggleEnabled) {
                    val point = POINT()
                    if (User32.INSTANCE.GetCursorPos(point)) {
                        val primaryBtn = getPrimaryButtonCode()
                        val isDown = (User32.INSTANCE.GetAsyncKeyState(primaryBtn).toInt() and 0x8000) != 0
                        
                        val hwnd = User32.INSTANCE.GetForegroundWindow()
                        val rect = com.sun.jna.platform.win32.WinDef.RECT()
                        if (hwnd != null) {
                            User32.INSTANCE.GetWindowRect(hwnd, rect)
                        }
                        
                        buffer[index % bufferSize] = MouseSample(loopStartTime, point.x, point.y, isDown, rect.left, rect.top)
                        index++
                        
                        if (index >= bufferSize) {
                            // Prevent integer overflow for long sessions
                            if (index >= bufferSize * 2) {
                                index -= bufferSize
                            }
                            
                            // Collect the contiguous segment where mouse is DOWN within the buffer
                            val segment = mutableListOf<MouseSample>()
                            for (i in 0 until bufferSize) {
                                // Traverse from newest to oldest
                                val sample = buffer[(index - 1 - i + bufferSize) % bufferSize]
                                if (!sample.isPrimaryDown) break
                                segment.add(sample)
                            }
                            segment.reverse() // Order from oldest to newest
                            
                            if (segment.size >= 10) { // At least 150ms of continuous dragging
                                var minX = segment[0].x
                                var maxX = segment[0].x
                                var minY = segment[0].y
                                var maxY = segment[0].y
                                
                                for (p in segment) {
                                    minX = minOf(minX, p.x)
                                    maxX = maxOf(maxX, p.x)
                                    minY = minOf(minY, p.y)
                                    maxY = maxOf(maxY, p.y)
                                }
                                
                                val boundsX = maxX - minX
                                val boundsY = maxY - minY
                                
                                val windowMovedX = abs(segment.last().fgWindowX - segment.first().fgWindowX)
                                val windowMovedY = abs(segment.last().fgWindowY - segment.first().fgWindowY)
                                val isWindowDrag = windowMovedX > 5 || windowMovedY > 5
                                
                                // Bounding box constraint (rejects massive full-screen long drags)
                                if (boundsX <= 300 && boundsY <= 300 && !isWindowDrag) {
                                    val reversalsX = countReversals(segment) { it.x }
                                    val reversalsY = countReversals(segment) { it.y }
                                    
                                    // Trigger if enough reversals on either X or Y axis
                                    if (reversalsX >= 3 || reversalsY >= 3) {
                                        withContext(Dispatchers.Main) { onTrigger() }
                                        
                                        // Reset buffer logically and physically to prevent stale reads
                                        index = 0 
                                        for (i in 0 until bufferSize) {
                                            buffer[i] = MouseSample(0L, 0, 0, false, 0, 0)
                                        }
                                        delay(1000) // Cooldown to prevent double-firing
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // If disabled, reset index so buffer is clean when re-enabled
                    if (index > 0) {
                        index = 0
                        for (i in 0 until bufferSize) {
                            buffer[i] = MouseSample(0L, 0, 0, false, 0, 0)
                        }
                    }
                }
                
                delay(15) // ~66Hz Polling rate
                
                // Wake up detector (if thread hung for 5000ms+ due to sleep/suspend)
                if (System.currentTimeMillis() - loopStartTime > 5000L) {
                    index = 0
                    for (i in 0 until bufferSize) {
                        buffer[i] = MouseSample(0L, 0, 0, false, 0, 0)
                    }
                    withContext(Dispatchers.Main) { onWake?.invoke() }
                }
            }
        }
    }
    
    private fun countReversals(segment: List<MouseSample>, axisSelector: (MouseSample) -> Int): Int {
        if (segment.isEmpty()) return 0
        
        var lastExtremum = axisSelector(segment[0])
        var currentDir = 0 // 1 for positive, -1 for negative
        var reversals = 0
        val threshold = 15 // px to count as a deliberate directional movement
        
        for (i in 1 until segment.size) {
            val v = axisSelector(segment[i])
            val diff = v - lastExtremum
            
            if (currentDir == 0) {
                if (abs(diff) >= threshold) {
                    currentDir = if (diff > 0) 1 else -1
                    lastExtremum = v
                }
            } else {
                if (currentDir == 1) { // Was moving positive
                    if (v > lastExtremum) {
                        lastExtremum = v // Push extremum further
                    } else if (lastExtremum - v >= threshold) { // Reversed negative
                        currentDir = -1
                        reversals++
                        lastExtremum = v
                    }
                } else { // Was moving negative
                    if (v < lastExtremum) {
                        lastExtremum = v
                    } else if (v - lastExtremum >= threshold) { // Reversed positive
                        currentDir = 1
                        reversals++
                        lastExtremum = v
                    }
                }
            }
        }
        return reversals
    }
    
    fun stop() { job?.cancel() }
}
