package com.markmusic.player.visual

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.markmusic.player.LocalVisualActive
import com.markmusic.player.audio.AudioMeterBus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * MARK: original articulated 3-D music mascot, NOT an animated photograph.
 *
 * This uses only Android's built-in OpenGL ES 2.0. Every visible character part
 * is a lit three-dimensional mesh with perspective, depth buffer, directional
 * lighting, specular metal and moving joints. No GMS, camera or mic required.
 *
 * The existing ExoPlayer PCM tee supplies real rhythm/onset notifications.
 * When paused or out of foreground, Mark returns to a relaxed pose.
 */
@Composable
fun MarkDancer3D(
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val active = LocalVisualActive.current.value
    val renderer = remember { MarkDancerRenderer() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val surface = remember(context) {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 24, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            preserveEGLContextOnPause = true
            setZOrderOnTop(false)
        }
    }
    DisposableEffect(surface, active) {
        if (active) surface.onResume() else surface.onPause()
        onDispose { surface.onPause() }
    }
    renderer.musicPlaying = playing && active
    Box(modifier) {
        AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Pure, unit-testable dance envelope with cooldown-triggered PCM onsets.
 * The audio callback is the sole source of beat impulses; no synthetic beat
 * clock keeps the character dancing when the music is silent.
 */
internal fun danceImpulse(previous: Float, elapsedSeconds: Float, beatStrength: Float): Float {
    val decayed = previous.coerceIn(0f, 1f) * exp(-elapsedSeconds.coerceIn(0f, .5f) * 6.0f)
    return max(decayed, beatStrength.coerceIn(0f, 1f))
}

internal fun danceMovement(
    envelope: Float,
    pulse: Float,
    left: Float,
    right: Float
): Float {
    val energy = ((left + right) * .5f).coerceIn(0f, 1f)
    return ((energy * .42f + pulse * .58f) * envelope).coerceIn(0f, 1f)
}

private class MarkDancerRenderer : GLSurfaceView.Renderer {
    @Volatile var musicPlaying: Boolean = false

    private var shader = 0
    private var posLocation = 0
    private var normLocation = 0
    private var modelLocation = 0
    private var mvpLocation = 0
    private var colorLocation = 0
    private var alphaLocation = 0
    private var emissionLocation = 0
    private lateinit var sphere: Mesh
    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val vp = FloatArray(16)
    private val root = FloatArray(16)
    private var lastFrameNs = 0L
    private var startedNs = 0L
    private var seenBeatCount = 0L
    private var beatPulse = 0f
    private var smoothLevel = 0f
    private var dancePhase = 0f
    private var lastBeatNs = 0L
    private var beatPeriod = .52f
    private var poseEnergy = 0f
    private var w = 1
    private var h = 1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        shader = linkShader(VERTEX, FRAGMENT)
        GLES20.glUseProgram(shader)
        posLocation = GLES20.glGetAttribLocation(shader, "aPosition")
        normLocation = GLES20.glGetAttribLocation(shader, "aNormal")
        modelLocation = GLES20.glGetUniformLocation(shader, "uModel")
        mvpLocation = GLES20.glGetUniformLocation(shader, "uMvp")
        colorLocation = GLES20.glGetUniformLocation(shader, "uColor")
        alphaLocation = GLES20.glGetUniformLocation(shader, "uAlpha")
        emissionLocation = GLES20.glGetUniformLocation(shader, "uEmission")
        sphere = createSphere(24, 16)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(.021f, .028f, .066f, 1f)
        lastFrameNs = 0L
        startedNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        w = max(1, width)
        h = max(1, height)
        GLES20.glViewport(0, 0, w, h)
        val aspect = w.toFloat() / h
        // Portrait stage framed for the square now-playing visual area.
        Matrix.perspectiveM(projection, 0, 37f, aspect, .1f, 20f)
        Matrix.setLookAtM(view, 0, 0f, .08f, 6.65f, 0f, -.12f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.elapsedRealtimeNanos()
        if (startedNs == 0L) startedNs = now
        val dt = if (lastFrameNs == 0L) 0f else
            ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0f, .06f)
        lastFrameNs = now
        val frame = AudioMeterBus.levels.value
        val live = musicPlaying
        val measured = if (live) ((frame.left + frame.right) * .5f).coerceIn(0f, 1f) else 0f
        smoothLevel += (measured - smoothLevel) * (1f - exp(-dt * 9f))
        poseEnergy += ((if (live) 1f else 0f) - poseEnergy) *
            (1f - exp(-dt * if (live) 4.5f else 5.8f))

        if (live && frame.beatCount != seenBeatCount) {
            if (lastBeatNs > 0L) {
                val interval = ((now - lastBeatNs) / 1_000_000_000f)
                if (interval in .30f..1.20f) beatPeriod =
                    beatPeriod * .72f + interval * .28f
            }
            lastBeatNs = now
            seenBeatCount = frame.beatCount
            beatPulse = danceImpulse(beatPulse, 0f, frame.beatStrength)
        }
        if (!live) seenBeatCount = frame.beatCount
        beatPulse = danceImpulse(beatPulse, dt, 0f)

        // The phase re-locks to every detected real onset, not a random timer.
        dancePhase = if (live && lastBeatNs != 0L) {
            (((now - lastBeatNs) / 1_000_000_000f) /
                beatPeriod.coerceIn(.30f, 1.2f)) * (2.0 * PI).toFloat()
        } else 0f
        val amplitude = danceMovement(poseEnergy, beatPulse, frame.left, frame.right)
        val beat = beatPulse * poseEnergy
        val sway = sin(dancePhase) * (.08f + .27f * amplitude) * poseEnergy
        val bounce = .07f * amplitude + .16f * beat
        val bodyTurn = sin(dancePhase * .50f) * 13f * poseEnergy * amplitude
        val bodyLean = sway * 13f

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(shader)

        // Shadow, three-tier metallic platform and soft luminous stage rings.
        drawBall(identity(), 0f, -1.91f, -.16f, 1.65f, .074f, 1.00f,
            .038f, .065f, .16f)
        drawBall(identity(), 0f, -1.87f, -.06f, 1.49f, .048f, .87f,
            .14f, .23f, .34f)
        drawBall(identity(), 0f, -1.845f, .0f, 1.41f, .036f, .80f,
            .065f, .075f, .16f)
        drawBall(identity(), 0f, -1.825f, -.03f, 1.27f, .014f, .68f,
            .08f + .14f * beat, .28f + .25f * beat, .43f + .22f * beat,
            alpha = .88f, emit = .10f + .16f * beat)

        Matrix.setIdentityM(root, 0)
        Matrix.translateM(root, 0, 0f, bounce, 0f)
        Matrix.rotateM(root, 0, bodyTurn, 0f, 1f, 0f)
        Matrix.rotateM(root, 0, bodyLean, 0f, 0f, 1f)

        val cool = floatArrayOf(.10f, .70f, .90f)
        val violet = floatArrayOf(.60f, .30f, .86f)
        val silver = floatArrayOf(.72f, .79f, .87f)
        val midnight = floatArrayOf(.055f, .085f, .18f)
        val chest = floatArrayOf(.17f, .23f, .48f)

        // Torso, segmented cuirass, narrow neck, glowing shoulder joints.
        drawBall(root, 0f, -.01f, 0f, .52f, .70f, .36f, chest)
        drawBall(root, 0f, -.02f, .285f, .43f, .58f, .11f, midnight)
        drawBall(root, 0f, .51f, 0f, .22f, .18f, .22f, silver)
        drawBall(root, 0f, -.56f, 0f, .38f, .22f, .32f, violet)
        drawBall(root, 0f, -.09f, .402f, .34f, .29f, .043f, cool,
            alpha=.91f, emit=.11f + beat * .30f)

        // Two legs really bend at their knees; alternation follows audio.
        for (side in listOf(-1f, 1f)) {
            val lifting = max(0f, sin(dancePhase + if (side < 0f) 0f else PI.toFloat()))
                * (.26f + amplitude * .34f) * poseEnergy
            val hip = Point(side * .255f, -.65f, 0f)
            val knee = Point(side * (.31f + .09f * lifting), -1.18f + .10f * lifting,
                .10f + .18f * lifting)
            val ankle = Point(side * (.32f + .14f * lifting), -1.66f + .29f * lifting,
                .18f + .22f * lifting)
            limb(root, hip, knee, .145f, midnight)
            drawBall(root, knee.x, knee.y, knee.z, .16f, .16f, .16f, silver)
            limb(root, knee, ankle, .13f, violet)
            drawBall(root, ankle.x, ankle.y, ankle.z + .16f,
                .235f, .16f, .37f, midnight)
            drawBall(root, ankle.x, ankle.y + .025f, ankle.z + .385f,
                .205f, .075f, .086f, cool, emit=.045f)
        }

        // Articulated shoulders, elbows and wrist/cuffs sweep out of the screen.
        for (side in listOf(-1f, 1f)) {
            val swing = sin(dancePhase + if (side < 0f) PI.toFloat() else 0f)
                * (.23f + .31f * amplitude) * poseEnergy
            val raise = beat * .38f + abs(swing) * .20f
            val shoulder = Point(side * .54f, .36f, 0f)
            val elbow = Point(side * (.77f + .10f * amplitude),
                -.12f + raise + swing * .55f, .21f + .16f * amplitude)
            val hand = Point(side * (.99f + .18f * amplitude),
                -.45f + raise * 1.6f + swing, .37f + .30f * amplitude)
            drawBall(root, shoulder.x, shoulder.y, shoulder.z,
                .235f, .23f, .235f, silver)
            limb(root, shoulder, elbow, .15f, violet)
            drawBall(root, elbow.x, elbow.y, elbow.z, .155f, .155f, .155f, cool)
            limb(root, elbow, hand, .12f, midnight)
            drawBall(root, hand.x, hand.y, hand.z, .17f, .18f, .15f, silver)
            drawBall(root, hand.x, hand.y, hand.z + .135f,
                .13f, .07f, .055f, cool, emit=.18f + beat * .25f)
        }

        // Helmet is a full metallic sphere with sculpted faceplate and glowing eyes.
        val nod = sin(dancePhase) * beat * .055f
        drawBall(root, 0f, 1.03f + nod, 0f, .452f, .453f, .416f, violet)
        drawBall(root, 0f, 1.035f + nod, .295f, .357f, .285f, .153f,
            midnight)
        drawBall(root, -.147f, 1.074f + nod, .417f, .079f, .067f, .036f, cool,
            emit=.37f + beat * .30f)
        drawBall(root, .147f, 1.074f + nod, .417f, .079f, .067f, .036f, cool,
            emit=.37f + beat * .30f)
        drawBall(root, 0f, .929f + nod, .442f, .109f, .027f, .026f, silver)

        // Headphone cans and arch are 3-D, complete with metallic rims.
        for (side in listOf(-1f, 1f)) {
            drawBall(root, side * .444f, 1.035f + nod, -.005f,
                .142f, .236f, .223f, silver)
            drawBall(root, side * .519f, 1.035f + nod, .061f,
                .080f, .17f, .160f, midnight)
            drawBall(root, side * .587f, 1.035f + nod, .078f,
                .038f, .068f, .073f,
                if(live) cool else violet, emit=.29f + beat * .24f)
        }
        for (j in 0..18) {
            val arc = PI * (j / 18.0)
            val x = .483f * cos(arc).toFloat()
            val y = 1.06f + .48f * sin(arc).toFloat()
            drawBall(root, x, y + nod, -.043f, .063f, .070f, .077f, midnight)
        }

        // Three-dimensional raised 'M' logo across Mark's illuminated chest.
        val mColor = floatArrayOf(.92f, .97f, .99f)
        val st = -.16f
        val en = .16f
        for ((a,b) in listOf(
            Point(st,-.18f,.468f) to Point(st,.08f,.469f),
            Point(st,.08f,.469f) to Point(0f,-.05f,.480f),
            Point(0f,-.05f,.480f) to Point(en,.08f,.469f),
            Point(en,.08f,.469f) to Point(en,-.18f,.468f)
        )) limb(root, a, b, .026f, mColor, emit=.27f + beat*.4f)
        // LED scale on chest responds to real decoded PCM levels.
        for (i in 0..4) {
            val height = .025f + min(1f, smoothLevel * 1.5f) *
                (.015f + i * .009f)
            drawBall(root, -.11f + i*.056f, -.295f + height*.5f, .409f,
                .017f, height, .019f, cool,
                emit = .10f + (if(live) smoothLevel else 0f) * .22f)
        }

        // Subtle rim accent beneath the figure.
        drawBall(identity(), 0f, -1.83f, .46f, .45f, .013f, .085f,
            if(live) cool else violet, alpha=.65f, emit=.16f)
    }

    private fun identity(): FloatArray = FloatArray(16).also {
        Matrix.setIdentityM(it,0)
    }

    private data class Point(val x: Float, val y: Float, val z: Float)

    private fun limb(
        parent: FloatArray,
        a: Point, b: Point,
        radius: Float, rgb: FloatArray, emit: Float = 0f
    ) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(.001f)
        val local = identity()
        Matrix.translateM(local,0,(a.x+b.x)*.5f,(a.y+b.y)*.5f,(a.z+b.z)*.5f)
        val dot = (dy / len).coerceIn(-1f,1f)
        val rotation = Math.toDegrees(acos(dot).toDouble()).toFloat()
        val ax = dz
        val az = -dx
        val axisLength = sqrt(ax*ax+az*az)
        if (axisLength > .00001f) {
            Matrix.rotateM(local,0,rotation,ax/axisLength,0f,az/axisLength)
        } else if (dot < 0f) {
            Matrix.rotateM(local,0,180f,1f,0f,0f)
        }
        Matrix.scaleM(local,0,radius,len*.62f,radius)
        draw(parent,local,rgb,1f,emit)
    }

    private fun drawBall(
        parent: FloatArray,
        x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,
        rgb:FloatArray,alpha:Float=1f,emit:Float=0f
    )=drawBall(parent,x,y,z,sx,sy,sz,rgb[0],rgb[1],rgb[2],alpha,emit)

    private fun drawBall(
        parent: FloatArray,
        x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,
        red:Float,green:Float,blue:Float,alpha:Float=1f,emit:Float=0f
    ) {
        val local=identity()
        Matrix.translateM(local,0,x,y,z)
        Matrix.scaleM(local,0,sx,sy,sz)
        draw(parent,local,floatArrayOf(red,green,blue),alpha,emit)
    }

    private fun draw(
        parent:FloatArray, local:FloatArray,rgb:FloatArray,alpha:Float,emit:Float
    ) {
        val model=FloatArray(16)
        Matrix.multiplyMM(model,0,parent,0,local,0)
        val mvp=FloatArray(16)
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        GLES20.glUniformMatrix4fv(modelLocation,1,false,model,0)
        GLES20.glUniformMatrix4fv(mvpLocation,1,false,mvp,0)
        GLES20.glUniform3f(colorLocation,rgb[0],rgb[1],rgb[2])
        GLES20.glUniform1f(alphaLocation,alpha)
        GLES20.glUniform1f(emissionLocation,emit)
        sphere.buffer.position(0)
        GLES20.glEnableVertexAttribArray(posLocation)
        GLES20.glVertexAttribPointer(posLocation,3,GLES20.GL_FLOAT,false,24,sphere.buffer)
        sphere.buffer.position(3)
        GLES20.glEnableVertexAttribArray(normLocation)
        GLES20.glVertexAttribPointer(normLocation,3,GLES20.GL_FLOAT,false,24,sphere.buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,sphere.count)
    }

    private data class Mesh(val buffer:FloatBuffer,val count:Int)

    private fun createSphere(segments:Int,rows:Int):Mesh {
        val verts=ArrayList<Float>()
        fun point(phi:Double,theta:Double):FloatArray {
            val y=cos(phi).toFloat()
            val radius=sin(phi).toFloat()
            return floatArrayOf(
                radius*cos(theta).toFloat(),y,radius*sin(theta).toFloat()
            )
        }
        fun put(v:FloatArray) {
            verts.add(v[0]);verts.add(v[1]);verts.add(v[2])
            verts.add(v[0]);verts.add(v[1]);verts.add(v[2])
        }
        for (i in 0 until rows) for (j in 0 until segments) {
            val p0=PI*i/rows
            val p1=PI*(i+1)/rows
            val t0=2.0*PI*j/segments
            val t1=2.0*PI*(j+1)/segments
            val a=point(p0,t0)
            val b=point(p1,t0)
            val c=point(p1,t1)
            val d=point(p0,t1)
            put(a);put(b);put(c)
            put(a);put(c);put(d)
        }
        val bytes=ByteBuffer.allocateDirect(verts.size*4).order(ByteOrder.nativeOrder())
        val data=bytes.asFloatBuffer()
        verts.forEach { data.put(it) }
        data.position(0)
        return Mesh(data,verts.size/6)
    }

    private fun linkShader(v:String,f:String):Int {
        fun compile(type:Int,source:String):Int {
            val id=GLES20.glCreateShader(type)
            GLES20.glShaderSource(id,source)
            GLES20.glCompileShader(id)
            val out=IntArray(1)
            GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,out,0)
            if(out[0]==0) {
                val error=GLES20.glGetShaderInfoLog(id)
                GLES20.glDeleteShader(id)
                throw IllegalStateException("MARK 3D shader: $error")
            }
            return id
        }
        val vs=compile(GLES20.GL_VERTEX_SHADER,v)
        val fs=compile(GLES20.GL_FRAGMENT_SHADER,f)
        val id=GLES20.glCreateProgram()
        GLES20.glAttachShader(id,vs);GLES20.glAttachShader(id,fs)
        GLES20.glLinkProgram(id)
        val ok=IntArray(1)
        GLES20.glGetProgramiv(id,GLES20.GL_LINK_STATUS,ok,0)
        GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs)
        if(ok[0]==0)throw IllegalStateException("MARK 3D link: "+
            GLES20.glGetProgramInfoLog(id))
        return id
    }

    private companion object {
        const val VERTEX = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vWorld;
            void main() {
                vec4 world=uModel*vec4(aPosition,1.0);
                vWorld=world.xyz;
                vNormal=mat3(uModel)*aNormal;
                gl_Position=uMvp*vec4(aPosition,1.0);
            }
        """
        const val FRAGMENT = """
            precision mediump float;
            uniform vec3 uColor;
            uniform float uAlpha;
            uniform float uEmission;
            varying vec3 vNormal;
            varying vec3 vWorld;
            void main() {
                vec3 n=normalize(vNormal);
                vec3 l=normalize(vec3(-0.46,0.83,0.9));
                vec3 v=normalize(vec3(0.0,0.0,6.0)-vWorld);
                float diffuse=max(dot(n,l),0.0);
                vec3 halfAngle=normalize(l+v);
                float highlight=pow(max(dot(n,halfAngle),0.0),25.0);
                float rim=pow(1.0-max(dot(n,v),0.0),2.3);
                vec3 result=uColor*(0.20+0.75*diffuse);
                result+=vec3(0.70,0.85,1.0)*(highlight*0.58+rim*0.16);
                result+=uColor*uEmission;
                gl_FragColor=vec4(min(result,vec3(1.0)),uAlpha);
            }
        """
    }
}
