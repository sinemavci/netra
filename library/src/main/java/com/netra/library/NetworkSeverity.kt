package com.netra.library

enum class NetworkSeverity {
    NORMAL, // < 500ms
    DEGRADED, // 500ms - 1500ms (Trigger: Show Progress, Load Thumbnails)
}