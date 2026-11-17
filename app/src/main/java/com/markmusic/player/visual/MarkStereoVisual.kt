package com.markmusic.player.visual

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import com.markmusic.player.audio.AudioMeterBus
import com.markmusic.player.audio.AudioMeterFrame
import com.markmusic.player.audio.FrequencyBandLevels
import com.markmusic.player.audio.StereoFrequencyBands
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * MARK Stereo Visuals — working visual slice 1 of 3.
 *
 * Analog Studio and Spectrum Matrix use six **actual** independent L/R PCM
 * FFT bands and original player RMS/peak values. This is precisely drawn 2D
 * with a layered, beveled instrument face. Genuine 3D mesh renderer is not
 * claimed yet; later design stages will replace only the case/glass layers.
 */
@Composable
fun MarkStereoVisual(
    analog: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val levels by AudioMeterBus.levels.collectAsState()
    val bands by AudioMeterBus.frequencyBands.collectAsState()
    Canvas(modifier) {
        val native = drawContext.canvas.nativeCanvas
        native.save()
        native.scale(size.width / 900f, size.height / 900f)
        val painter = MeterPainter(native)
        painter.frame(analog, playing)
        if (analog) painter.analog(levels, bands)
        else painter.matrix(levels, bands)
        native.restore()
    }
}

private class MeterPainter(private val c: AndroidCanvas) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bold = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val cyan = 0xFF20D9FAL.toInt()
    private val pink = 0xFFFF52C9L.toInt()
    private val violet = 0xFF986BFFL.toInt()
    private val bronze = 0xFFFFD796L.toInt()
    private val white = 0xFFEAF9FFL.toInt()

    private fun clear() {
        p.reset()
        p.isAntiAlias = true
    }
    private fun box(
        x: Float, y: Float, w: Float, h: Float, radius: Float,
        color: Int, stroke: Float = 0f, strokeColor: Int = 0
    ) {
        clear()
        p.color = color
        c.drawRoundRect(x, y, x+w, y+h, radius, radius, p)
        if (stroke > 0f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = stroke
            p.color = strokeColor
            c.drawRoundRect(x, y, x+w, y+h, radius, radius, p)
        }
    }
    private fun gradient(
        x: Float,y: Float,w: Float,h: Float,radius: Float,
        top: Int,bottom: Int
    ) {
        clear()
        p.shader = LinearGradient(x,y,x,y+h,top,bottom,Shader.TileMode.CLAMP)
        c.drawRoundRect(x,y,x+w,y+h,radius,radius,p)
        p.shader=null
    }
    private fun line(
        x1: Float,y1: Float,x2: Float,y2: Float,color:Int,
        width:Float=2f,glow:Float=0f
    ) {
        clear()
        p.color=color
        p.strokeWidth=width
        p.strokeCap=Paint.Cap.ROUND
        if(glow>0f) p.setShadowLayer(glow,0f,0f,color)
        c.drawLine(x1,y1,x2,y2,p)
        p.clearShadowLayer()
    }
    private fun dot(x:Float,y:Float,r:Float,color:Int,glow:Float=0f) {
        clear();p.color=color
        if(glow>0f)p.setShadowLayer(glow,0f,0f,color)
        c.drawCircle(x,y,r,p)
        p.clearShadowLayer()
    }
    private fun text(
        value:String, x:Float, y:Float,
        size:Float=24f,color:Int=white,heavy:Boolean=false,
        centered:Boolean=false,letterSpacing:Float=0f
    ) {
        clear();p.color=color;p.textSize=size
        p.typeface=if(heavy)bold else regular
        p.textAlign=if(centered)Paint.Align.CENTER else Paint.Align.LEFT
        // Monospaced digits keep the readout stable on changes in values.
        if(letterSpacing==0f) c.drawText(value,x,y,p)
        else {
            var px=if(centered) x-(p.measureText(value)+letterSpacing*(value.length-1))/2f else x
            for(ch in value) {
                val g=ch.toString()
                c.drawText(g,px,y,p)
                px+=p.measureText(g)+letterSpacing
            }
        }
    }
    private fun bolt(x:Float,y:Float) {
        dot(x,y,12f,0xFF020711.toInt())
        dot(x,y,8f,0xFF9DB8C9.toInt())
        dot(x-1.5f,y-1.5f,5.5f,0xFF39465C.toInt())
        line(x-5f,y+3f,x+5f,y-3f,0xFF0D1423.toInt(),2f)
    }
    fun frame(analog:Boolean,playing:Boolean) {
        gradient(0f,0f,900f,900f,0f,0xFF020513.toInt(),0xFF0B1230.toInt())
        // Three offset beveled metal edges create physical case depth.
        gradient(18f,24f,864f,852f,58f,0xFF65758C.toInt(),0xFF0B101E.toInt())
        gradient(28f,34f,844f,830f,51f,0xFF17263A.toInt(),0xFF02030C.toInt())
        box(34f,40f,832f,816f,47f,0xFF080E1C.toInt(),3f,0xFF243B56.toInt())
        gradient(48f,54f,804f,788f,40f,0xFF17273A.toInt(),0xFF030711.toInt())
        line(78f,111f,823f,111f,0xFF344F6A.toInt(),2f)
        line(73f,786f,826f,786f,0xFF293A56.toInt(),2f)
        for(x in listOf(64f,838f))for(y in listOf(76f,824f))bolt(x,y)
        text("MARK",92f,101f,41f,white,true,letterSpacing=4f)
        text("STEREO  •  "+(if(analog)"ANALOG STUDIO" else "SPECTRUM MATRIX"),
            280f,95f,20f,0xFFADC6D8.toInt(),true,letterSpacing=2.3f)
        dot(760f,91f,5f,if(playing)0xFF39FCB3.toInt()else 0xFF64758B.toInt(),if(playing)7f else 0f)
        text(if(playing)"PCM LIVE" else "PAUSED",776f,97f,16f,
            if(playing)0xFF96FFE1.toInt() else 0xFF7B89A3.toInt(),true)
        text("MARK AUDIO  /  PRECISION STEREO METERS",450f,829f,17f,
            0xFF7595B1.toInt(),true,true,1.4f)
    }
    fun analog(level:AudioMeterFrame, bands:StereoFrequencyBands) {
        gauge(92f,143f,340f,406f,"LEFT",cyan,level.left,level.peakLeft)
        gauge(468f,143f,340f,406f,"RIGHT",pink,level.right,level.peakRight)
        // deep machined lower instrument bay with six frequency meters
        gradient(82f,569f,736f,195f,24f,0xFF050913.toInt(),0xFF152539.toInt())
        box(87f,575f,726f,183f,20f,0xFF060D1A.toInt(),2f,0xFF546279.toInt())
        text("FREQUENCY BANDS  /  L",112f,604f,19f,cyan,true,letterSpacing=1f)
        text("FREQUENCY BANDS  /  R",487f,604f,19f,pink,true,letterSpacing=1f)
        val left=listOf(bands.left.low,bands.left.mid,bands.left.high)
        val right=listOf(bands.right.low,bands.right.mid,bands.right.high)
        val labels=listOf("LOW","MID","HIGH")
        for(ch in 0..1) for(b in 0..2) {
            val x=103f+ch*374f+b*111f
            tinyLadder(x,618f,92f,91f,if(ch==0)cyan else pink,
                if(ch==0)left[b] else right[b],14)
            text(labels[b],x+46f,740f,17f,0xFFC3CFE2.toInt(),true,true)
        }
        line(450f,593f,450f,737f,0xFF33516A.toInt(),3f)
    }
    private fun gauge(
        x:Float,y:Float,w:Float,h:Float,name:String,accent:Int,
        rms:Float,peak:Float
    ) {
        // Outer cast aluminium, warm illuminated needle chamber.
        gradient(x-3f,y-2f,w+6f,h+8f,27f,0xFF95A8BD.toInt(),0xFF252A38.toInt())
        gradient(x+7f,y+9f,w-14f,h-14f,22f,0xFF121826.toInt(),0xFF020307.toInt())
        gradient(x+20f,y+26f,w-40f,h-77f,12f,0xFFF9D8A2.toInt(),0xFF5E3823.toInt())
        // Analog glass and deep shadow rim.
        box(x+17f,y+22f,w-34f,h-71f,15f,0x15000000,3f,0xFF1C1C24.toInt())
        val pivotX=x+w/2f
        val pivotY=y+330f
        val centerAngle=0f
        // Curved scale printed on the dial. Needles sweep -60 .. +60 deg.
        for(i in 0..12) {
            val a=(-1.08f+2.16f*i/12f)+centerAngle
            val inner=if(i%2==0)157f else 165f
            val outer=178f
            val x1=pivotX+sin(a)*inner
            val y1=pivotY-cos(a)*inner
            val x2=pivotX+sin(a)*outer
            val y2=pivotY-cos(a)*outer
            line(x1,y1,x2,y2,if(i>=10)0xFFC63230.toInt()
                else 0xFF402A1B.toInt(),if(i%2==0)4f else 2f)
        }
        for((i,label) in listOf("-24","-18","-12","-6","0","+3").withIndex()){
            val a=-1.02f+2.04f*i/5f
            text(label,pivotX+sin(a)*133f,pivotY-cos(a)*133f+8f,
                20f,0xFF3E231B.toInt(),true,true)
        }
        text("MARK",pivotX,y+208f,34f,0xFF6F4634.toInt(),true,true,2f)
        text("VU",pivotX,y+252f,22f,0xFF68432D.toInt(),true,true,2f)
        // mechanical pointer with light highlights and red tip
        val swing=-1.05f+2.10f*rms.coerceIn(0f,1f)
        val nx=pivotX+sin(swing)*236f
        val ny=pivotY-cos(swing)*236f
        line(pivotX,pivotY,nx,ny,0xFF3C1C13.toInt(),7f)
        line(pivotX-1f,pivotY,nx-1f,ny,0xFFE7D4B8.toInt(),2f)
        dot(pivotX,pivotY,19f,0xFF30303D.toInt())
        dot(pivotX,pivotY,10f,0xFFB6B6B8.toInt())
        dot(pivotX-3f,pivotY-3f,3f,white)
        // reflected glass specular stripe
        line(x+33f,y+51f,x+w-33f,y+47f,0x46FFFFFF,3f)
        text(name,pivotX,y+h-21f,23f,accent,true,true,3f)
        dot(x+w-41f,y+h-31f,5f,if(peak>.91f)0xFFFF4545.toInt()
            else 0xFF39445D.toInt(),if(peak>.91f)9f else 0f)
    }

    fun matrix(level:AudioMeterFrame,bands:StereoFrequencyBands) {
        gradient(82f,145f,736f,618f,24f,0xFF344457.toInt(),0xFF080E1C.toInt())
        gradient(90f,153f,720f,603f,20f,0xFF010309.toInt(),0xFF0C1528.toInt())
        box(100f,162f,700f,580f,18f,0xFF040B18.toInt(),2f,0xFF27374A.toInt())
        text("LEFT CHANNEL",266f,207f,29f,cyan,true,true,3f)
        text("RIGHT CHANNEL",637f,207f,29f,pink,true,true,3f)
        line(448f,195f,448f,702f,0xFF1E3A52.toInt(),3f)
        for(i in 0..6){
            val y=262f+i*58f
            line(118f,y,781f,y,0xFF152337.toInt(),1f)
        }
        val nums=listOf(
            bands.left.low,bands.left.mid,bands.left.high,
            bands.right.low,bands.right.mid,bands.right.high
        )
        val labels=listOf("LOW","MID","HIGH")
        for(idx in 0..5) {
            val x=(if(idx<3)124f else 496f)+(idx%3)*110f
            val v=nums[idx].coerceIn(0f,1f)
            bigLadder(x,260f,75f,379f,v,if(idx<3)cyan else pink)
            text(labels[idx%3],x+38f,672f,24f,
                if(idx<3)0xFF9FF6FF.toInt() else 0xFFFFA6E6.toInt(),
                true,true,2f)
            text("%02d".format((v*99).toInt()),x+38f,718f,27f,
                white,true,true)
        }
        text("L  %02d".format((level.left.coerceIn(0f,1f)*99).toInt()),
            145f,239f,18f,0xFF6895B1.toInt(),true)
        text("R  %02d".format((level.right.coerceIn(0f,1f)*99).toInt()),
            520f,239f,18f,0xFF986F92.toInt(),true)
    }
    private fun bigLadder(x:Float,y:Float,w:Float,h:Float,value:Float,accent:Int) {
        val segments=20
        val gap=7f
        val bar=(h-(segments-1)*gap)/segments
        box(x-9f,y-10f,w+18f,h+20f,13f,0xFF030711.toInt(),
            2f,0xFF23394A.toInt())
        val lit=(value*segments).toInt()
        for(i in 0 until segments) {
            val yy=y+h-(i+1)*bar-i*gap
            val led=when {
                i>=17 -> 0xFFFF4848L.toInt()
                i>=12 -> 0xFFFFC24AL.toInt()
                else -> 0xFF4CF49AL.toInt()
            }
            val on=i<lit
            box(x,yy,w,bar,5f,if(on)led else 0xFF19252BL.toInt())
            if(on) {
                clear();p.color=led;p.setShadowLayer(12f,0f,0f,led)
                c.drawRoundRect(x+5f,yy+1f,x+w-5f,yy+bar-1f,4f,4f,p)
                p.clearShadowLayer()
            } else {
                line(x+8f,yy+2f,x+w-8f,yy+2f,0xFF30404A.toInt(),1f)
            }
        }
        line(x-7f,y+h-(value*h),x+w+7f,y+h-(value*h),accent,2f,3f)
    }
    private fun tinyLadder(
        x:Float,y:Float,w:Float,h:Float,color:Int,value:Float,segments:Int
    ) {
        val lit=(value.coerceIn(0f,1f)*segments).toInt()
        val gap=3.6f
        val height=(h-gap*(segments-1))/segments
        for(i in 0 until segments){
            val yy=y+h-(i+1)*height-i*gap
            val cc=if(i>=11)0xFFFF6257.toInt()
                else if(i>=8)0xFFFFB960.toInt() else color
            box(x+5f,yy,w-10f,height,3f,
                if(i<lit)cc else 0xFF1B2638.toInt())
        }
    }
}
