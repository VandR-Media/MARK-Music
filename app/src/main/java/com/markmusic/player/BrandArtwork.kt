package com.markmusic.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.LinearGradient
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Offline-generated cosmic artwork for the MARK Music visual identity. */
internal object BrandArtwork {
    val appBackground: ImageBitmap by lazy { render(0).asImageBitmap() }
    val welcome: ImageBitmap by lazy { render(1).asImageBitmap() }
    val dialogBackground: ImageBitmap by lazy { render(2).asImageBitmap() }
    val logo: ImageBitmap by lazy { render(3).asImageBitmap() }

    private fun render(mode: Int): Bitmap {
        val bmp = Bitmap.createBitmap(640, 900, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val colors = when(mode) {
            1 -> intArrayOf(0xFF08142D.toInt(),0xFF29205F.toInt(),0xFF751C83.toInt())
            2 -> intArrayOf(0xFF07152D.toInt(),0xFF173B70.toInt(),0xFF35165E.toInt())
            else -> intArrayOf(0xFF07152D.toInt(),0xFF15295A.toInt(),0xFF32145D.toInt())
        }
        p.shader = LinearGradient(0f,0f,640f,900f,colors,null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,640f,900f,p); p.shader=null
        p.color=0x9957CFFF.toInt()
        for(i in 0..90) c.drawCircle(((i*173+41)%640).toFloat(),((i*293+19)%900).toFloat(),if(i%7==0) 2.4f else 1.1f,p)
        when(mode) {
            2 -> {
                p.color=0xFFE5F4FF.toInt(); p.setShadowLayer(28f,0f,0f,0xFF55BFFF.toInt())
                c.drawCircle(370f,360f,245f,p); p.clearShadowLayer()
                p.color=0xFF273B72.toInt(); c.drawCircle(445f,300f,220f,p)
                p.color=0xFF080F29.toInt(); c.drawOval(160f,690f,500f,840f,p)
                p.strokeWidth=44f; p.strokeCap=Paint.Cap.ROUND
                c.drawLine(325f,560f,250f,720f,p); c.drawLine(325f,560f,455f,700f,p); c.drawCircle(325f,485f,65f,p)
                p.color=0xFF4BDFFF.toInt(); p.style=Paint.Style.STROKE; p.strokeWidth=5f; c.drawCircle(325f,485f,80f,p)
            }
            else -> {
                val cx=if(mode==1) 320f else 335f; val cy=if(mode==1) 375f else 390f
                p.color=0xFF0A1029.toInt()
                val head=Path().apply { moveTo(cx-125,cy-145); cubicTo(cx-125,cy-270,cx+40,cy-280,cx+125,cy-190); cubicTo(cx+160,cy-110,cx+130,cy-25,cx+165,cy+20); lineTo(cx+110,cy+55); cubicTo(cx+110,cy+165,cx-30,cy+190,cx-100,cy+130); cubicTo(cx-165,cy+75,cx-155,cy-70,cx-125,cy-145); close() }
                c.drawPath(head,p); p.color=0xFF101C3B.toInt(); c.drawOval(cx-190,cy+125,cx+190,cy+440,p)
                p.style=Paint.Style.STROKE; p.strokeWidth=36f; p.color=0xFF18234A.toInt()
                c.drawArc(cx-160,cy-250,cx+160,cy+115,185f,170f,false,p)
                p.style=Paint.Style.FILL; p.color=0xFF1D4383.toInt(); c.drawRoundRect(cx-170,cy-35,cx-100,cy+100,25f,25f,p)
                p.style=Paint.Style.STROKE; p.strokeWidth=9f; p.color=0xFF50DFFF.toInt(); c.drawCircle(cx-135,cy+30,52f,p)
                p.color=0xFFB65CFF.toInt(); c.drawCircle(cx-135,cy+30,40f,p)
                p.style=Paint.Style.FILL; p.color=0xFF9AEFFF.toInt(); p.textSize=44f; p.textAlign=Paint.Align.CENTER
                c.drawText("M",cx-135,cy+45,p)
                p.color=0xAA35DFFF.toInt(); p.strokeWidth=8f; p.style=Paint.Style.STROKE
                for(i in 0..27) { val x=35f+i*21f; val h=35f+((i*47)%130); c.drawLine(x,690f-h,x,690f,p) }
                p.color=0xFFFF65D2.toInt(); p.strokeWidth=5f
                val wave=Path().apply { moveTo(50f,790f); for(i in 0..100) lineTo(50f+i*5.3f,790f+kotlin.math.sin(i*.4).toFloat()*(12f+(i%8)*3f)) }; c.drawPath(wave,p)
            }
        }
        p.style=Paint.Style.STROKE; p.strokeWidth=6f; p.color=0xAA50DFFF.toInt()
        c.drawRoundRect(14f,14f,626f,886f,70f,70f,p)
        return bmp
    }
}
