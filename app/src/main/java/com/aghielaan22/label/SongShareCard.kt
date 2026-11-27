package com.aghielaan22.label

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class ShareCardFormat(val label: String, val height: Int) {
    Square("Square", 1080),
    Story("Stories", 1920)
}

internal const val MAX_CARD_LYRIC_LINES = 3
private const val CARD_WIDTH = 1080
private const val LEFT = 76f
private const val RIGHT = 1004f

internal suspend fun loadCardCover(context: Context, song: Song): Bitmap? {
    val url = song.thumbnailUrl ?: return null
    return try {
        val request = ImageRequest.Builder(context).data(url).size(900, 900).allowHardware(false).build()
        (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/** The preview and PNG share these coordinates and the same text layout. */
internal fun renderSongCard(
    song: Song,
    format: ShareCardFormat,
    lyrics: List<String>,
    cover: Bitmap?,
    width: Int = CARD_WIDTH
): Bitmap {
    val factor = width / CARD_WIDTH.toFloat()
    val card = Bitmap.createBitmap(width, (format.height * factor).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(card).apply { scale(factor, factor) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val ink = Color.rgb(12, 10, 24)
    val sampled = cover?.getPixel(cover.width / 2, cover.height / 3) ?: Color.rgb(120, 75, 201)
    val accent = ColorUtils.blendARGB(Color.rgb(177, 134, 255), ColorUtils.setAlphaComponent(sampled, 255), 0.43f)
    val night = ColorUtils.blendARGB(ink, accent, 0.25f)
    val height = format.height.toFloat()
    val quote = lyrics.joinToString("\n").takeIf { it.isNotBlank() }
    val safeTop = if (format == ShareCardFormat.Story) 235f else 60f
    val safeBottom = if (format == ShareCardFormat.Story) height - 245f else height - 62f

    paint.shader = LinearGradient(0f, 0f, 1040f, height, night, ink, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), height, paint)
    paint.shader = null

    paint.color = ColorUtils.setAlphaComponent(accent, 36)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    for (radius in listOf(275f, 332f, 389f, 446f, 503f)) {
        canvas.drawCircle(RIGHT, safeTop + 65f, radius, paint)
    }
    paint.style = Paint.Style.FILL

    fun text(value: String, x: Float, y: Float, size: Float, lines: Int, bold: Boolean, textColor: Int): Int {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, textPaint, (RIGHT - x).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(lines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    text("LAbel", LEFT, safeTop, 44f, 1, true, Color.WHITE)
    paint.color = ColorUtils.setAlphaComponent(accent, 120)
    canvas.drawRect(LEFT, safeTop + 64f, RIGHT, safeTop + 66f, paint)

    val artTop = safeTop + 105f
    val artSize = when {
        format == ShareCardFormat.Story && quote == null -> 900f
        format == ShareCardFormat.Story -> 770f
        quote == null -> 595f
        else -> 370f
    }
    val artLeft = (CARD_WIDTH - artSize) / 2f
    val frame = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(frame, 36f, 36f, Path.Direction.CW) })
    if (cover != null) {
        val side = minOf(cover.width, cover.height)
        val x = (cover.width - side) / 2
        val y = (cover.height - side) / 2
        paint.color = Color.WHITE
        canvas.drawBitmap(cover, Rect(x, y, x + side, y + side), frame, paint)
    } else {
        paint.shader = LinearGradient(frame.left, frame.top, frame.right, frame.bottom, accent, night, Shader.TileMode.CLAMP)
        canvas.drawRect(frame, paint)
        paint.shader = null
        paint.color = night
        canvas.drawCircle(frame.centerX(), frame.centerY(), artSize * 0.29f, paint)
        paint.color = Color.WHITE
        paint.textSize = artSize * 0.27f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("♫", frame.centerX(), frame.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
    }
    canvas.restore()

    val titleTop = frame.bottom + 41f
    val titleHeight = text(song.title, LEFT, titleTop, if (quote == null) 70f else 59f, 2, true, Color.WHITE)
    val artistTop = titleTop + titleHeight + 10f
    val artistHeight = text(song.artist, LEFT, artistTop, 38f, 1, false, ColorUtils.blendARGB(accent, Color.WHITE, 0.58f))

    if (quote != null) {
        val quoteTop = artistTop + artistHeight + 25f
        val available = (safeBottom - quoteTop).coerceAtLeast(92f)
        val size = if (format == ShareCardFormat.Story) 54f else 40f
        val lineLimit = (available / (size * 1.25f)).toInt().coerceIn(1, 7)
        paint.color = accent
        canvas.drawRoundRect(LEFT, quoteTop + 3f, LEFT + 7f, safeBottom, 4f, 4f, paint)
        text(quote, LEFT + 32f, quoteTop, size, lineLimit, false, Color.WHITE)
    }
    return card
}

internal suspend fun songCardIntent(
    context: Context,
    song: Song,
    format: ShareCardFormat,
    lyrics: List<String>,
    cover: Bitmap?
): Intent = shareCardPngIntent(context, "Song card", "${song.title} by ${song.artist}\n${song.id}") {
    renderSongCard(song, format, lyrics, cover)
}

internal suspend fun shareCardPngIntent(
    context: Context,
    label: String,
    text: String,
    render: () -> Bitmap
): Intent = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "share-cards")
    if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("Cannot create card folder")
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    val file = File.createTempFile("label-", ".png", dir)
    try {
        val card = render()
        try {
            file.outputStream().use {
                if (!card.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Card export failed")
            }
        } finally {
            card.recycle()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.sharecards", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri(label, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } catch (e: Exception) {
        file.delete()
        throw e
    }
}