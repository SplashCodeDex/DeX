package com.dexstudios.dex.desktop.jna

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.POINT
import kotlinx.coroutines.*
import kotlin.math.abs

object WiggleToOpenService {
    private var job: Job? = null
    
    fun start(onTrigger: () -> Unit) {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            println("WiggleToOpenService: Not on Windows. Skipping JNA mouse hook.")
            return
        }
        
        job = CoroutineScope(Dispatchers.IO).launch {
            val buffer = IntArray(20)
            var index = 0
            
            while (isActive) {
                val point = POINT()
                if (User32.INSTANCE.GetCursorPos(point)) {
                    buffer[index % 20] = point.x
                    index++
                    
                    if (index >= 20) {
                        var reversals = 0
                        var minX = buffer[0]
                        var maxX = buffer[0]
                        
                        var lastDiff = 0
                        for (i in 1..19) {
                            val curr = buffer[(index - 20 + i) % 20]
                            val prev = buffer[(index - 20 + i - 1) % 20]
                            val diff = curr - prev
                            
                            minX = minOf(minX, curr)
                            maxX = maxOf(maxX, curr)
                            
                            if (abs(diff) > 5) {
                                if (lastDiff > 0 && diff < 0 || lastDiff < 0 && diff > 0) {
                                    reversals++
                                }
                                lastDiff = diff
                            }
                        }
                        
                        val lButton = User32.INSTANCE.GetAsyncKeyState(0x01).toInt()
                        val rButton = User32.INSTANCE.GetAsyncKeyState(0x02).toInt()
                        val isMouseUp = (lButton and 0x8000) == 0 && (rButton and 0x8000) == 0
                        
                        if (reversals >= 3 && (maxX - minX) <= 150 && isMouseUp) {
                            withContext(Dispatchers.Main) { onTrigger() }
                            index = 0
                            delay(1000)
                        }
                    }
                }
                delay(50)
            }
        }
    }
    
    fun stop() { job?.cancel() }
}
