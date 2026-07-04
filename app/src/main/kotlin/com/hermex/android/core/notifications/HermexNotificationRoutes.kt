package com.hermex.android.core.notifications

import java.net.URLEncoder

object HermexNotificationRoutes {
    fun sessions(): String = "hermex://sessions"

    fun session(sessionId: String): String = "hermex://session/${sessionId.encodePathSegment()}"

    fun tasks(): String = "hermex://tasks"

    fun task(jobId: String): String = "hermex://task/${jobId.encodePathSegment()}"
}

private fun String.encodePathSegment(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
