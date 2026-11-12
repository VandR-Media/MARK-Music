package com.markmusic.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.LinearGradient
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Approved MARK Audio logo and separate approved welcome artwork. */
internal object BrandArtwork {
    fun logo(context: Context): ImageBitmap =
        android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.mark_logo).asImageBitmap()

    fun welcome(context: Context): ImageBitmap =
        android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.mark_welcome).asImageBitmap()

    val appBackground: ImageBitmap by lazy { renderBackground().asImageBitmap() }
    val dialogBackground: ImageBitmap get() = appBackground

    private fun renderBackground(): Bitmap {
        val bmp = Bitmap.createBitmap(640, 900, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 640f, 900f,
            intArrayOf(0xFF07152D.toInt(), 0xFF15295A.toInt(), 0xFF32145D.toInt()),
            null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 640f, 900f, p)
        p.shader = null
        p.color = 0x9957CFFF.toInt()
        for (i in 0..90) c.drawCircle(((i * 173 + 41) % 640).toFloat(),
            ((i * 293 + 19) % 900).toFloat(), if (i % 7 == 0) 2.4f else 1.1f, p)
        return bmp
    }
}
