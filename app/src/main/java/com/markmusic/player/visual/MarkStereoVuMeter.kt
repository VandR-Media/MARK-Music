package com.markmusic.player.visual

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.markmusic.player.audio.AudioMeterBus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Animated genuine stereo PCM VU instrument; no second player, WebView, microphone or DEMO. */
internal fun vuNeedleAngle(level: Float): Float =
    -145f + (if (level.isFinite()) level.coerceIn(0f, 1f) else 0f) * 110f

@Composable
internal fun MarkStereoVuMeter(playing: Boolean, modifier: Modifier = Modifier) {
    val levels by AudioMeterBus.levels.collectAsState()
    val left by animateFloatAsState(
        targetValue = if (playing) levels.left else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "left-vu"
    )
    val right by animateFloatAsState(
        targetValue = if (playing) levels.right else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "right-vu"
    )
    Canvas(modifier.fillMaxSize()) {
        drawIntoCanvas { layer ->
            val c = layer.nativeCanvas
            val scale = minOf(size.width / 1000f, size.height / 700f)
            c.save()
            c.translate((size.width - 1000f * scale) / 2f,
                (size.height - 700f * scale) / 2f)
            c.scale(scale, scale)
            StereoVuArtwork(c).draw(left, right)
            c.restore()
        }
    }
}

/**
 * All markings and needles are vector painted, independently of image resolution.
 * Cabinet / amber scales are inspired by the reference VU photograph approved by user.
 * Moving pointers are drawn last, never baked into a stationary bitmap.
 */
private class StereoVuArtwork(private val c: AndroidCanvas) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame = RectF()
    private val ivory = AColor.rgb(255, 238, 204)
    private val dark = AColor.rgb(8, 11, 15)
    private val gold = AColor.rgb(218, 151, 57)

    private fun rounded(x: Float,y:Float,w:Float,h:Float,r:Float,color:Int,
        stroke:Float=0f,gradient:IntArray?=null) {
        p.reset()
        p.isAntiAlias=true
        p.color=color
        p.style=if(stroke>0f) Paint.Style.STROKE else Paint.Style.FILL
        p.strokeWidth=stroke
        p.shader=if(gradient!=null) LinearGradient(x,y,x,y+h,gradient,null,Shader.TileMode.CLAMP) else null
        c.drawRoundRect(RectF(x,y,x+w,y+h),r,r,p)
        p.shader=null
        p.style=Paint.Style.FILL
    }
    private fun text(t:String,x:Float,y:Float,size:Float,color:Int,
        bold:Boolean=false,script:Boolean=false) {
        p.reset(); p.isAntiAlias=true;p.color=color;p.textSize=size
        p.textAlign=Paint.Align.CENTER
        p.typeface=if(script) Typeface.create("serif",Typeface.BOLD_ITALIC)
            else Typeface.create("sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(t,x,y,p)
    }
    private fun line(x:Float,y:Float,x2:Float,y2:Float,color:Int,width:Float) {
        p.reset();p.isAntiAlias=true;p.color=color;p.strokeWidth=width
        p.strokeCap=Paint.Cap.ROUND
        c.drawLine(x,y,x2,y2,p)
    }
    private fun circle(x:Float,y:Float,r:Float,color:Int) {
        p.reset();p.isAntiAlias=true;p.color=color
        c.drawCircle(x,y,r,p)
    }
    private fun polar(cx:Float,cy:Float,r:Float,angle:Float):Pair<Float,Float>{
        val radians=angle*PI/180.0
        return Pair(cx+(cos(radians)*r).toFloat(),cy+(sin(radians)*r).toFloat())
    }
    private fun screw(x:Float,y:Float) {
        circle(x+1f,y+2f,13f,AColor.BLACK)
        p.reset();p.isAntiAlias=true
        p.shader=RadialGradient(x-4f,y-5f,18f,intArrayOf(
            AColor.rgb(255,229,155),AColor.rgb(140,99,44),AColor.rgb(23,23,25)),
            null,Shader.TileMode.CLAMP)
        c.drawCircle(x,y,11f,p);p.shader=null
        line(x-5f,y-5f,x+5f,y+5f,dark,2.8f)
        line(x-5f,y+5f,x+5f,y-5f,dark,2.8f)
    }
    private fun dial(cx:Float,level:Float,channel:String) {
        val x=cx-231f
        rounded(x-7f,65f,476f,469f,39f,AColor.BLACK)
        rounded(x,70f,462f,453f,34f,dark,gradient=intArrayOf(
            AColor.rgb(119,85,42),AColor.rgb(31,26,22),AColor.rgb(12,13,16)))
        rounded(x+7f,78f,448f,438f,32f,gold,stroke=3f)
        rounded(x+12f,84f,438f,424f,28f,ivory,gradient=intArrayOf(
            AColor.rgb(255,253,239),AColor.rgb(255,231,184),AColor.rgb(236,184,105)))
        // Soft fixed inner-glass highlight, independent of audio signal.
        rounded(x+17f,88f,427f,43f,19f,AColor.argb(38,255,255,255),
            gradient=intArrayOf(AColor.argb(85,255,255,255),AColor.TRANSPARENT))

        val py=433f
        val ticks=listOf("-20","-10","-7","-5","-3","-2","-1","0","+1","+2","+3")
        for(i in ticks.indices) {
            val angle=-145f+i*11f
            val large=i==0||i==1||i==4||i==7||i==10
            val red=i>=8
            val a=polar(cx,py,255f,angle)
            val b=polar(cx,py,255f+(if(large)21f else 12f),angle)
            line(a.first,a.second,b.first,b.second,
                if(red) AColor.rgb(195,36,28) else AColor.rgb(43,39,35),
                if(large) 3f else 2f)
            val label=polar(cx,py,290f,angle)
            text(ticks[i],label.first,label.second+7f,
                if(large) 19f else 16f,
                if(red) AColor.rgb(190,27,24) else AColor.rgb(32,33,32),true)
        }
        // True analogue red overload zone.
        val arc=RectF(cx-255f,py-255f,cx+255f,py+255f)
        p.reset();p.isAntiAlias=true;p.style=Paint.Style.STROKE;p.strokeWidth=8f
        p.color=AColor.rgb(217,30,25)
        c.drawArc(arc,-57f,22f,false,p);p.style=Paint.Style.FILL

        text("VU",cx-155f,165f,27f,AColor.rgb(48,42,34),true)
        text("VU",cx+155f,165f,27f,AColor.rgb(48,42,34),true)
        text("MARK AUDIO",cx,310f,38f,AColor.rgb(68,44,30),script=true)
        text(channel,cx,343f,17f,AColor.rgb(72,55,43),true)

        // Mechanical movement visible through a lower semicircular aperture.
        p.reset();p.isAntiAlias=true;p.color=AColor.rgb(28,25,24)
        c.drawOval(RectF(cx-93f,413f,cx+93f,539f),p)
        rounded(cx-93f,434f,186f,83f,14f,AColor.rgb(43,33,23))
        for(i in 0..7) {
            val angle=i*PI/4.0
            circle(cx+(cos(angle)*35).toFloat(),
                476f+(sin(angle)*30).toFloat(),8f,
                if(i%2==0) gold else AColor.rgb(99,103,104))
        }
        circle(cx,473f,30f,AColor.rgb(135,107,59))
        circle(cx,473f,17f,AColor.rgb(35,39,40))
        screw(cx-114f,492f);screw(cx+114f,492f)

        // Mechanical pointer: actual instantaneous level after Compose smoothing.
        val theta=vuNeedleAngle(level)
        val tip=polar(cx,py,240f,theta)
        line(cx+3f,py+4f,tip.first+3f,tip.second+4f,
            AColor.argb(95,46,32,21),8f)
        line(cx,py,tip.first,tip.second,AColor.rgb(18,19,18),5f)
        circle(cx,py,11f,AColor.rgb(44,44,43))
        circle(cx-2f,py-3f,4f,AColor.rgb(180,159,105))
    }
    fun draw(left:Float,right:Float){
        rounded(0f,0f,1000f,700f,39f,AColor.BLACK,
            gradient=intArrayOf(AColor.rgb(53,57,61),AColor.rgb(6,9,12),AColor.rgb(36,39,40)))
        rounded(7f,8f,986f,684f,33f,AColor.rgb(87,94,96),stroke=3f)
        // Cabinet grain / brushed-metal texture.
        for(i in 1..90) {
            val y=i*7.7f
            line(16f,y,984f,y,
                if(i%3==0) AColor.argb(25,209,212,214) else AColor.argb(18,0,0,0),1f)
        }
        dial(260f,left,"LEFT")
        dial(740f,right,"RIGHT")

        rounded(325f,548f,350f,107f,21f,AColor.rgb(10,8,7))
        rounded(333f,554f,334f,95f,18f,gold,stroke=3f)
        rounded(341f,561f,318f,81f,13f,AColor.rgb(36,29,22),
            gradient=intArrayOf(AColor.rgb(87,62,35),AColor.rgb(20,18,17)))
        text("Mark Audio",500f,617f,49f,AColor.rgb(242,188,95),script=true)
        for(i in 0..3) {
            rounded(95f,568f+i*21f,205f,5f,3f,AColor.rgb(3,5,6))
            rounded(700f,568f+i*21f,205f,5f,3f,AColor.rgb(3,5,6))
        }
        for(pt in listOf(Pair(30f,34f),Pair(970f,34f),Pair(30f,665f),Pair(970f,665f))) {
            screw(pt.first,pt.second)
        }
    }
}
