package com.hermex.android.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hermex.android.core.network.dto.MessageAttachment

object AttachmentFileOpener {

    fun openAttachment(context: Context, attachment: MessageAttachment) {
        val mimeType = attachment.mime ?: "application/octet-stream"
        val url = attachment.path ?: return

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (fallback.resolveActivity(context.packageManager) != null) {
                context.startActivity(fallback)
            }
        }
    }
}
