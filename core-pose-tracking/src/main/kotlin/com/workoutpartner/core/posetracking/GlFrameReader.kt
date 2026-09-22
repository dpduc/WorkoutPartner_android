package com.workoutpartner.core.posetracking

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Renders a hardware [MediaCodec] decoder's output to readable pixels via OpenGL, instead of
 * [android.media.ImageReader]'s CPU-side plane read — see [VideoPoseTracker]'s own doc comment for
 * why. [outputSurface] is the [Surface] to configure the decoder with; call [readFrame] once per
 * frame *after* [MediaCodec.releaseOutputBuffer] was called for it with `render = true`.
 *
 * All GL/EGL work happens on one dedicated thread ([executor]) for the object's whole lifetime — an
 * EGL context is thread-affined, and this class's caller (a coroutine on [Dispatchers.Default]) can
 * otherwise resume on a different underlying thread after every `suspend` call. [runOnGlThread]
 * hands work to that thread and blocks the caller until it's done, which is fine here: each call
 * does a small bounded amount of work (draw one frame, or read one frame back), not an I/O wait.
 */
internal class GlFrameReader(size: Size) {

    private val width = size.width
    private val height = size.height

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val frameSyncLock = java.lang.Object()
    private var frameAvailable = false
    private val stMatrix = FloatArray(16)

    private lateinit var eglDisplay: EGLDisplay
    private lateinit var eglContext: EGLContext
    private lateinit var eglSurface: EGLSurface
    private lateinit var surfaceTexture: SurfaceTexture
    private lateinit var surface: Surface
    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var stMatrixHandle = 0
    private var externalTextureId = 0
    private var fboTextureId = 0
    private var framebufferId = 0

    private val vertexBuffer = directFloatBufferOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val texCoordBuffer = directFloatBufferOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
    private val pixelBuffer: ByteBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())

    /** The decoder's output target. Valid only after construction, which sets this up synchronously. */
    val outputSurface: Surface = runOnGlThread { setUp() }

    /**
     * Waits for the frame most recently rendered via `releaseOutputBuffer(index, render = true)` to
     * actually arrive (rendering is asynchronous), draws it into an off-screen framebuffer through a
     * pass-through shader (applying [SurfaceTexture.getTransformMatrix], which — for a decoder's
     * output specifically — already includes the container's rotation hint, on top of the producer's
     * own crop/stride), and reads that framebuffer's pixels back into a correctly-oriented [Bitmap].
     */
    fun readFrame(): Bitmap = runOnGlThread { drawAndReadPixels() }

    fun release() {
        runOnGlThread { tearDown() }
        executor.shutdown()
    }

    private fun <T> runOnGlThread(block: () -> T): T =
        try {
            executor.submit(block).get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

    private fun setUp(): Surface {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw IllegalStateException("eglGetDisplay failed.")
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw IllegalStateException("eglInitialize failed.")
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            throw IllegalStateException("eglChooseConfig failed.")
        }
        val eglConfig = configs[0]!!

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) throw IllegalStateException("eglCreateContext failed.")

        // A 1x1 pbuffer only so a surface can be current — actual output goes to an FBO below, never this one.
        val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) throw IllegalStateException("eglCreatePbufferSurface failed.")

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw IllegalStateException("eglMakeCurrent failed.")
        }

        // The external texture the decoder renders into, via the SurfaceTexture/Surface below.
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        externalTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(externalTextureId)
        surfaceTexture.setDefaultBufferSize(width, height)
        surfaceTexture.setOnFrameAvailableListener {
            synchronized(frameSyncLock) {
                frameAvailable = true
                frameSyncLock.notifyAll()
            }
        }
        surface = Surface(surfaceTexture)

        program = buildProgram()
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        stMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")

        // The off-screen target the shader actually draws into — glReadPixels reads this, not the pbuffer above.
        val fboTextures = IntArray(1)
        GLES20.glGenTextures(1, fboTextures, 0)
        fboTextureId = fboTextures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        val fbos = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        framebufferId = fbos[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTextureId, 0)
        val fbStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (fbStatus != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw IllegalStateException("Framebuffer incomplete: 0x${fbStatus.toString(16)}")
        }

        return surface
    }

    private fun drawAndReadPixels(): Bitmap {
        awaitNewFrame()
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(stMatrix)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        texCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, stMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glFinish()

        pixelBuffer.rewind()
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)
        pixelBuffer.rewind()
        val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(pixelBuffer)
        // glReadPixels' row 0 is the bottom of the image (OpenGL's window-coordinate convention) —
        // the opposite of Bitmap's own top-down row order. No further rotation needed here: the
        // container's rotation hint is already baked into stMatrix (see this class's own doc comment).
        val flip = Matrix().apply { postScale(1f, -1f, width / 2f, height / 2f) }
        return Bitmap.createBitmap(raw, 0, 0, width, height, flip, true)
    }

    /** `releaseOutputBuffer(index, render = true)` returning doesn't mean the frame has arrived yet — it's
     * delivered asynchronously via [SurfaceTexture.OnFrameAvailableListener]; this blocks until it has. */
    private fun awaitNewFrame() {
        synchronized(frameSyncLock) {
            if (!frameAvailable) {
                frameSyncLock.wait(FRAME_WAIT_TIMEOUT_MS)
                if (!frameAvailable) throw IllegalStateException("Timed out waiting for a decoded frame.")
            }
            frameAvailable = false
        }
    }

    private fun buildProgram(): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val builtProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(builtProgram, vertexShader)
        GLES20.glAttachShader(builtProgram, fragmentShader)
        GLES20.glLinkProgram(builtProgram)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(builtProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(builtProgram)
            GLES20.glDeleteProgram(builtProgram)
            throw IllegalStateException("Shader program link failed: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return builtProgram
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Shader compile failed: $log")
        }
        return shader
    }

    private fun tearDown() {
        runCatching { GLES20.glDeleteProgram(program) }
        runCatching { GLES20.glDeleteFramebuffers(1, intArrayOf(framebufferId), 0) }
        runCatching { GLES20.glDeleteTextures(2, intArrayOf(externalTextureId, fboTextureId), 0) }
        runCatching { surface.release() }
        runCatching { surfaceTexture.release() }
        if (this::eglDisplay.isInitialized && eglDisplay != EGL14.EGL_NO_DISPLAY) {
            runCatching { EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) }
            runCatching { EGL14.eglDestroySurface(eglDisplay, eglSurface) }
            runCatching { EGL14.eglDestroyContext(eglDisplay, eglContext) }
            runCatching { EGL14.eglTerminate(eglDisplay) }
        }
    }

    companion object {
        private const val FRAME_WAIT_TIMEOUT_MS = 2_500L

        private const val VERTEX_SHADER = """
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """

        private fun directFloatBufferOf(vararg values: Float): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(values)
                position(0)
            }
    }
}
