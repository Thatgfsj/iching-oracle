package com.thatgfsj.iching.ui.oracle

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.thatgfsj.iching.data.Hexagram

/**
 * Package id of the official DeepSeek Android app, verified via the
 * Google Play Store canonical URL
 * (https://play.google.com/store/apps/details?id=com.deepseek.chat).
 *
 * Used to route an ACTION_SEND intent straight at DeepSeek when it's
 * installed. If the user doesn't have DeepSeek, the chooser falls
 * back to the system share sheet — we don't want a hard failure.
 */
private const val DEEPSEEK_PACKAGE: String = "com.deepseek.chat"

/**
 * Build the share text. Kept in Chinese to match the rest of the app
 * and because DeepSeek's chat is primarily a Chinese product.
 */
fun formatHexagramShareText(hexagram: Hexagram): String = buildString {
    appendLine("【易经卜卦】")
    appendLine()
    appendLine("我抽到了第 ${hexagram.id} 卦：${hexagram.name_zh}（${hexagram.name_pinyin} / ${hexagram.name_en}）")
    appendLine()
    appendLine("卦辞：${hexagram.judgment}")
    appendLine("象传：${hexagram.image}")
    appendLine()
    append("请帮我解读这卦对当前处境的启示。")
}

/**
 * Fire the system share sheet, preferring the DeepSeek app if it's
 * installed. Falls back to the regular chooser (without `setPackage`)
 * if DeepSeek isn't present, so the user can still share to any
 * other chat app.
 */
fun shareToDeepSeek(context: Context, hexagram: Hexagram) {
    val text = formatHexagramShareText(hexagram)

    val deepSeekIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage(DEEPSEEK_PACKAGE)
    }

    // If DeepSeek accepts the intent, the chooser shows it on top;
    // if it doesn't (ActivityNotFoundException), drop the package
    // hint and fall back to the generic chooser.
    val finalIntent = try {
        context.packageManager.resolveActivity(deepSeekIntent, 0)
        Intent.createChooser(deepSeekIntent, "分享到 DeepSeek")
    } catch (_: Exception) {
        val generic = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        Intent.createChooser(generic, "分享卦象")
    }

    finalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(finalIntent)
    } catch (_: ActivityNotFoundException) {
        // No app at all handles ACTION_SEND — extremely unlikely on a
        // modern Android device, but we surface it instead of crashing.
        Toast.makeText(context, "未找到可分享的应用", Toast.LENGTH_SHORT).show()
    }
}