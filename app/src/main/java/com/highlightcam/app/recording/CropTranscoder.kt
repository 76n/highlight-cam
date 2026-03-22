package com.highlightcam.app.recording

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface
import com.highlightcam.app.tracking.CropWindow
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CropTranscoder
    @Inject
    constructor() {
        @Suppress("LongMethod", "TooGenericExceptionCaught", "NestedBlockDepth", "ReturnCount")
        fun transcode(
            inputFile: File,
            outputFile: File,
            cropTimeline: List<Pair<Long, CropWindow>>,
            clipStartTimeMs: Long,
        ): Boolean {
            if (cropTimeline.isEmpty()) return false

            var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
            var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
            var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
            var decoder: MediaCodec? = null
            var encoder: MediaCodec? = null
            var muxer: MediaMuxer? = null
            val extractor = MediaExtractor()
            var texId = -1

            try {
                extractor.setDataSource(inputFile.absolutePath)
                var videoTrackIdx = -1
                var audioTrackIdx = -1
                var videoFormat: MediaFormat? = null
                var audioFormat: MediaFormat? = null

                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    when {
                        mime.startsWith("video/") && videoTrackIdx < 0 -> {
                            videoTrackIdx = i
                            videoFormat = fmt
                        }
                        mime.startsWith("audio/") && audioTrackIdx < 0 -> {
                            audioTrackIdx = i
                            audioFormat = fmt
                        }
                    }
                }

                if (videoTrackIdx < 0 || videoFormat == null) return false

                val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
                val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
                val mime = videoFormat.getString(MediaFormat.KEY_MIME) ?: return false
                val bitRate =
                    if (videoFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        videoFormat.getInteger(MediaFormat.KEY_BIT_RATE)
                    } else {
                        width * height * BIT_RATE_FACTOR
                    }
                val frameRate =
                    if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
                    } else {
                        DEFAULT_FRAME_RATE
                    }

                val encoderFormat =
                    MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                        setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                    }

                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val encoderSurface = encoder.createInputSurface()

                val eglSetup = setupEgl(encoderSurface)
                eglDisplay = eglSetup.display
                eglContext = eglSetup.context
                eglSurface = eglSetup.surface
                texId = createOesTexture()

                val surfaceTexture = android.graphics.SurfaceTexture(texId)
                surfaceTexture.setDefaultBufferSize(width, height)
                val decoderSurface = Surface(surfaceTexture)

                decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(videoFormat, decoderSurface, null, 0)

                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                encoder.start()
                decoder.start()
                extractor.selectTrack(videoTrackIdx)

                val program = buildShaderProgram()
                val (posLoc, texCoordLoc, texMatLoc) = getShaderLocations(program)

                var muxerVideoTrack = -1
                var muxerAudioTrack = -1
                var muxerStarted = false
                var inputDone = false
                var decoderDone = false
                val info = MediaCodec.BufferInfo()
                val transformMatrix = FloatArray(MATRIX_SIZE)

                while (!decoderDone) {
                    if (!inputDone) {
                        val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inIdx >= 0) {
                            val buf = decoder.getInputBuffer(inIdx) ?: continue
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (outIdx >= 0) {
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.size > 0) {
                            decoder.releaseOutputBuffer(outIdx, true)
                            surfaceTexture.updateTexImage()
                            surfaceTexture.getTransformMatrix(transformMatrix)

                            val presentationTimeUs = info.presentationTimeUs
                            val frameTimeMs = clipStartTimeMs + presentationTimeUs / 1000L
                            val crop = findCropForTime(cropTimeline, frameTimeMs)

                            drawCroppedFrame(
                                program, posLoc, texCoordLoc, texMatLoc,
                                texId, transformMatrix, crop, width, height,
                            )
                            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                        } else {
                            decoder.releaseOutputBuffer(outIdx, false)
                        }
                        if (eos) decoderDone = true
                    }

                    drainEncoder(encoder, muxer, info) { trackIdx ->
                        if (!muxerStarted) {
                            muxerVideoTrack = trackIdx
                            if (audioFormat != null) {
                                muxerAudioTrack = muxer!!.addTrack(audioFormat)
                            }
                            muxer!!.start()
                            muxerStarted = true
                        }
                        muxerVideoTrack
                    }
                }

                encoder.signalEndOfInputStream()
                @Suppress("UnsafeCallOnNullableType")
                drainEncoderFinal(encoder, muxer!!, info, muxerVideoTrack)

                if (muxerStarted && audioTrackIdx >= 0 && muxerAudioTrack >= 0) {
                    copyAudioTrack(inputFile, muxer, muxerAudioTrack, audioTrackIdx)
                }

                decoder.stop()
                decoder.release()
                decoder = null
                encoder.stop()
                encoder.release()
                encoder = null

                if (muxerStarted) muxer.stop()
                muxer.release()
                muxer = null

                surfaceTexture.release()
                decoderSurface.release()
                encoderSurface.release()
                GLES20.glDeleteProgram(program)
                GLES20.glDeleteTextures(1, intArrayOf(texId), 0)

                return outputFile.exists() && outputFile.length() > 0
            } catch (e: Throwable) {
                Timber.e(e, "CropTranscoder failed")
                return false
            } finally {
                try {
                    decoder?.release()
                } catch (_: Throwable) {
                }
                try {
                    encoder?.release()
                } catch (_: Throwable) {
                }
                try {
                    muxer?.release()
                } catch (_: Throwable) {
                }
                extractor.release()
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglTerminate(eglDisplay)
                }
            }
        }

        private data class EglSetup(
            val display: EGLDisplay,
            val context: EGLContext,
            val surface: EGLSurface,
        )

        private fun setupEgl(surface: Surface): EglSetup {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(display, version, 0, version, 1)

            val configAttribs =
                intArrayOf(
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_NONE,
                )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(display, configs[0]!!, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

            val surfAttribs = intArrayOf(EGL14.EGL_NONE)
            val eglSurface = EGL14.eglCreateWindowSurface(display, configs[0]!!, surface, surfAttribs, 0)

            EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)

            return EglSetup(display, context, eglSurface)
        }

        private fun createOesTexture(): Int {
            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texIds[0])
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return texIds[0]
        }

        private fun buildShaderProgram(): Int {
            val vertSrc =
                """
                attribute vec4 aPosition;
                attribute vec4 aTexCoord;
                uniform mat4 uTexMatrix;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = (uTexMatrix * aTexCoord).xy;
                }
                """.trimIndent()

            val fragSrc =
                """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTexCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTexCoord);
                }
                """.trimIndent()

            val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc)
            val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            return prog
        }

        private fun compileShader(
            type: Int,
            source: String,
        ): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }

        private data class ShaderLocations(val pos: Int, val texCoord: Int, val texMatrix: Int)

        private fun getShaderLocations(program: Int): ShaderLocations {
            val pos = GLES20.glGetAttribLocation(program, "aPosition")
            val tex = GLES20.glGetAttribLocation(program, "aTexCoord")
            val mat = GLES20.glGetUniformLocation(program, "uTexMatrix")
            return ShaderLocations(pos, tex, mat)
        }

        @Suppress("LongParameterList")
        private fun drawCroppedFrame(
            program: Int,
            posLoc: Int,
            texCoordLoc: Int,
            texMatLoc: Int,
            texId: Int,
            transformMatrix: FloatArray,
            crop: CropWindow,
            width: Int,
            height: Int,
        ) {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUseProgram(program)

            val quadCoords =
                floatArrayOf(
                    -1f,
                    -1f,
                    1f,
                    -1f,
                    -1f,
                    1f,
                    1f,
                    1f,
                )
            val quadBuf = createFloatBuffer(quadCoords)

            val rect = crop.toRect()
            val texCoords =
                floatArrayOf(
                    rect.left,
                    rect.bottom,
                    rect.right,
                    rect.bottom,
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.top,
                )
            val texBuf = createFloatBuffer(texCoords)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texId)

            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, quadBuf)
            GLES20.glEnableVertexAttribArray(posLoc)

            GLES20.glVertexAttribPointer(texCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texBuf)
            GLES20.glEnableVertexAttribArray(texCoordLoc)

            GLES20.glUniformMatrix4fv(texMatLoc, 1, false, transformMatrix, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, QUAD_VERTEX_COUNT)

            GLES20.glDisableVertexAttribArray(posLoc)
            GLES20.glDisableVertexAttribArray(texCoordLoc)
        }

        private fun createFloatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer
                .allocateDirect(data.size * FLOAT_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(data)
                    position(0)
                }

        private fun drainEncoder(
            encoder: MediaCodec,
            muxer: MediaMuxer?,
            info: MediaCodec.BufferInfo,
            onFormatChanged: (Int) -> Int,
        ) {
            var trackIdx = -1
            while (true) {
                val outIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIdx = onFormatChanged(muxer!!.addTrack(encoder.outputFormat))
                    }
                    outIdx >= 0 -> {
                        val buf = encoder.getOutputBuffer(outIdx) ?: break
                        if (info.size > 0 && muxer != null && trackIdx >= 0) {
                            muxer.writeSampleData(trackIdx, buf, info)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                    else -> break
                }
            }
        }

        private fun drainEncoderFinal(
            encoder: MediaCodec,
            muxer: MediaMuxer,
            info: MediaCodec.BufferInfo,
            trackIdx: Int,
        ) {
            while (true) {
                val outIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    val buf = encoder.getOutputBuffer(outIdx) ?: break
                    if (info.size > 0 && trackIdx >= 0) {
                        muxer.writeSampleData(trackIdx, buf, info)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                }
            }
        }

        @SuppressLint("WrongConstant")
        private fun copyAudioTrack(
            inputFile: File,
            muxer: MediaMuxer,
            muxerAudioTrack: Int,
            sourceAudioTrack: Int,
        ) {
            val ext = MediaExtractor()
            try {
                ext.setDataSource(inputFile.absolutePath)
                ext.selectTrack(sourceAudioTrack)
                val buf = ByteBuffer.allocate(AUDIO_BUFFER_SIZE)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val size = ext.readSampleData(buf, 0)
                    if (size < 0) break
                    info.presentationTimeUs = ext.sampleTime
                    info.flags = ext.sampleFlags
                    info.size = size
                    info.offset = 0
                    muxer.writeSampleData(muxerAudioTrack, buf, info)
                    ext.advance()
                }
            } finally {
                ext.release()
            }
        }

        companion object {
            private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65
            private const val TIMEOUT_US = 10_000L
            private const val BIT_RATE_FACTOR = 4
            private const val DEFAULT_FRAME_RATE = 30
            private const val I_FRAME_INTERVAL = 1
            private const val MATRIX_SIZE = 16
            private const val QUAD_VERTEX_COUNT = 4
            private const val FLOAT_BYTES = 4
            private const val AUDIO_BUFFER_SIZE = 256 * 1024

            fun findCropForTime(
                timeline: List<Pair<Long, CropWindow>>,
                timeMs: Long,
            ): CropWindow {
                if (timeline.isEmpty()) return CropWindow.FULL_FRAME
                var best = timeline.first().second
                var bestDist = Long.MAX_VALUE
                for ((t, cw) in timeline) {
                    val dist = kotlin.math.abs(t - timeMs)
                    if (dist < bestDist) {
                        bestDist = dist
                        best = cw
                    }
                }
                return best
            }
        }
    }
