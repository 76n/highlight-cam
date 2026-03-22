package com.highlightcam.app.recording

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipAssembler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @SuppressLint("WrongConstant")
        suspend fun assemble(
            segmentFiles: List<File>,
            segmentDurationsUs: List<Long>,
            trimLeadingUs: Long = 0L,
            maxDurationUs: Long = Long.MAX_VALUE,
        ): Result<Uri> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val outputDir = File(context.cacheDir, "hc_output").also { it.mkdirs() }
                    val outputFile = File(outputDir, "hc_${System.currentTimeMillis()}.mp4")

                    val muxer =
                        MediaMuxer(
                            outputFile.absolutePath,
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                        )

                    var videoTrackIndex = -1
                    var audioTrackIndex = -1
                    var muxerStarted = false
                    val buffer = ByteBuffer.allocate(BUFFER_SIZE)
                    val bufferInfo = MediaCodec.BufferInfo()

                    for ((segIdx, file) in segmentFiles.withIndex()) {
                        if (!file.exists()) continue

                        val segStartOffsetUs =
                            timestampOffsetUs(segIdx, segmentDurationsUs, trimLeadingUs)
                        if (segStartOffsetUs > maxDurationUs) break

                        val extractor = MediaExtractor()
                        try {
                            extractor.setDataSource(file.absolutePath)
                            val offset = segStartOffsetUs

                            if (!muxerStarted) {
                                for (t in 0 until extractor.trackCount) {
                                    val format = extractor.getTrackFormat(t)
                                    val mime =
                                        format.getString(MediaFormat.KEY_MIME) ?: continue
                                    when {
                                        mime.startsWith("video/") ->
                                            videoTrackIndex = muxer.addTrack(format)
                                        mime.startsWith("audio/") ->
                                            audioTrackIndex = muxer.addTrack(format)
                                    }
                                }
                                muxer.start()
                                muxerStarted = true
                            }

                            for (t in 0 until extractor.trackCount) {
                                val format = extractor.getTrackFormat(t)
                                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                                val muxerTrack =
                                    when {
                                        mime.startsWith("video/") -> videoTrackIndex
                                        mime.startsWith("audio/") -> audioTrackIndex
                                        else -> continue
                                    }
                                if (muxerTrack < 0) continue

                                extractor.selectTrack(t)

                                while (true) {
                                    val sampleSize = extractor.readSampleData(buffer, 0)
                                    if (sampleSize < 0) break

                                    val adjustedTimeUs = extractor.sampleTime + offset
                                    if (adjustedTimeUs < 0) {
                                        extractor.advance()
                                        continue
                                    }
                                    if (adjustedTimeUs > maxDurationUs) break

                                    bufferInfo.presentationTimeUs = adjustedTimeUs
                                    bufferInfo.flags = extractor.sampleFlags
                                    bufferInfo.size = sampleSize
                                    bufferInfo.offset = 0

                                    muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                                    extractor.advance()
                                }

                                extractor.unselectTrack(t)
                            }
                        } finally {
                            extractor.release()
                        }
                    }

                    if (muxerStarted) {
                        muxer.stop()
                    }
                    muxer.release()

                    check(outputFile.exists() && outputFile.length() > 0) {
                        "Muxer produced empty or missing file: ${outputFile.absolutePath}"
                    }

                    val uri = saveToMediaStore(outputFile)
                    outputFile.delete()

                    verifyMediaStoreEntry(uri)

                    uri
                }
            }

        private fun saveToMediaStore(file: File): Uri {
            val resolver = context.contentResolver

            val values =
                ContentValues().apply {
                    put(
                        MediaStore.Video.Media.DISPLAY_NAME,
                        "hc_${System.currentTimeMillis()}.mp4",
                    )
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Video.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_MOVIES}/HighlightCam",
                        )
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

            val uri =
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore insert failed")

            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Failed to open output stream for $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val update = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                resolver.update(uri, update, null, null)
            }

            Timber.d("Clip saved to MediaStore: %s", uri)
            return uri
        }

        private fun verifyMediaStoreEntry(uri: Uri) {
            val resolver = context.contentResolver
            resolver.query(uri, arrayOf(MediaStore.Video.Media.SIZE), null, null, null)
                ?.use { cursor ->
                    check(cursor.moveToFirst()) { "MediaStore row missing for $uri" }
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
                    check(size > 0) { "MediaStore entry has zero size for $uri" }
                } ?: throw IllegalStateException("MediaStore query returned null for $uri")
        }

        companion object {
            private const val BUFFER_SIZE = 1024 * 1024

            fun timestampOffsetUs(
                segmentIndex: Int,
                segmentDurationsUs: List<Long>,
                trimLeadingUs: Long,
            ): Long {
                val accumulated = segmentDurationsUs.take(segmentIndex).sum()
                return accumulated - trimLeadingUs
            }
        }
    }
