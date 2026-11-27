package com.aghielaan22.label

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.text.NumberFormat

internal suspend fun yearReviewCardIntent(context: Context, review: YearReview): Intent {
    val caption = buildString {
        appendLine("My ${review.year} in LAbel")
        appendLine(review.coverage)
        append("${review.plays} plays · ${review.trackCount} tracks · ${review.artistCount} artists")
    }
    return shareCardPngIntent(context, "Year in review", caption) { renderYearReviewCard(review) }
}

private fun renderYearReviewCard(review: YearReview): Bitmap {
    val card = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(card)
    val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    val violet = Color.rgb(200, 172, 255)
    val soft = Color.rgb(235, 223, 255)
    val number = NumberFormat.getIntegerInstance()

    brush.shader = LinearGradient(
        40f, 0f, 980f, 1300f,
        Color.rgb(49, 28, 92), Color.rgb(11, 8, 23), Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, 1080f, 1350f, brush)
    brush.shader = null
    brush.style = Paint.Style.STROKE
    brush.color = Color.argb(42, 202, 176, 255)
    brush.strokeWidth = 2f
    for (index in 0..13) canvas.drawCircle(975f, 170f, 215f + index * 17f, brush)
    brush.style = Paint.Style.FILL

    fun text(value: String, x: Float, y: Float, size: Float, width: Int, textColor: Int, bold: Boolean = false, lines: Int = 1) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
            .setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    fun divider(y: Float) {
        brush.color = Color.argb(100, 202, 176, 255)
        canvas.drawRect(80f, y, 1000f, y + 2f, brush)
    }

    text("LAbel", 80f, 70f, 46f, 920, Color.WHITE, bold = true)
    text("${review.year} in review", 80f, 179f, 89f, 920, Color.WHITE, bold = true)
    text(review.coverage, 80f, 291f, 36f, 920, soft)
    divider(367f)

    val formattedPlays = number.format(review.plays)
    text(formattedPlays, 76f, 407f, if (formattedPlays.length > 8) 119f else 170f, 920, Color.WHITE, bold = true)
    text("Plays", 86f, 601f, 42f, 900, violet)
    text("${number.format(review.trackCount)} tracks", 86f, 697f, 56f, 440, soft, bold = true)
    text("${number.format(review.artistCount)} artists", 555f, 697f, 56f, 445, soft, bold = true)
    divider(789f)

    review.topTrack?.let {
        text("Top track", 80f, 834f, 34f, 900, violet, bold = true)
        text(it.song.title, 80f, 887f, 58f, 920, Color.WHITE, bold = true, lines = 2)
        text(it.song.artist, 80f, 1033f, 37f, 920, soft)
    }
    review.topArtist?.let {
        text("Top artist", 80f, 1123f, 34f, 900, violet, bold = true)
        text(it, 80f, 1175f, 57f, 920, Color.WHITE, bold = true)
    }
    return card
}