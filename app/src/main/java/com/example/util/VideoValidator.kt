package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

data class VideoValidationSpec(
    val uri: Uri,
    val durationMs: Long,
    val durationSeconds: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val fileSizeBytes: Long,
    val thumbnailBitmap: Bitmap?,
    val thumbnailBase64: String,
    val rawBytes: ByteArray
) {
    val formattedDuration: String
        get() {
            val mins = durationSeconds / 60
            val secs = durationSeconds % 60
            return String.format("%d:%02d", mins, secs)
        }

    val resolutionLabel: String
        get() = "${width}x${height} (${minOf(width, height)}p)"
}

object VideoValidator {
    const val MAX_DURATION_MS = 5 * 60 * 1000L // 5 Minutes (300,000 ms)
    const val MAX_DURATION_SECONDS = 300L
    const val MAX_SHORT_SIDE_PX = 480 // 480p short side (e.g., 854x480 landscape or 480x854 portrait)
    const val MAX_LONG_SIDE_PX = 864 // Standard 16:9 480p is 854px; allow up to 864px for codec alignment
    const val MAX_FPS = 60
    const val MAX_FIRESTORE_VIDEO_BYTES = 12 * 1024 * 1024L // 12 MB practical ceiling for chunked Firestore storage

    /**
     * Validates a user-selected video against the strict social media upload rules:
     * - Maximum 5 minutes length (300 seconds)
     * - Maximum 480p resolution
     * - Maximum 60 FPS frame rate
     */
    fun validateAndExtractVideo(context: Context, uri: Uri): Result<VideoValidationSpec> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val durationSeconds = maxOf(1L, (durationMs + 500L) / 1000L)

            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0

            val width = if (rotation == 90 || rotation == 270) rawHeight else rawWidth
            val height = if (rotation == 90 || rotation == 270) rawWidth else rawHeight

            if (width <= 0 || height <= 0 || durationMs <= 0L) {
                return Result.failure(
                    IllegalArgumentException("Could not read valid video stream metadata from the selected file.")
                )
            }

            // Extract FPS using MediaExtractor first, fallback to MediaMetadataRetriever
            val detectedFps = extractFrameRate(context, uri, retriever, durationMs)

            // 1. Validate Duration <= 5 minutes (300s)
            if (durationMs > MAX_DURATION_MS) {
                val mins = durationSeconds / 60
                val secs = durationSeconds % 60
                return Result.failure(
                    IllegalArgumentException(
                        "Video length (${String.format("%d:%02d", mins, secs)}) exceeds the maximum 5-minute (5:00) limit."
                    )
                )
            }

            // 2. Validate Resolution <= 480p
            val shortSide = minOf(width, height)
            val longSide = maxOf(width, height)
            if (shortSide > MAX_SHORT_SIDE_PX || longSide > MAX_LONG_SIDE_PX) {
                return Result.failure(
                    IllegalArgumentException(
                        "Video resolution (${width}x${height} / ${shortSide}p) exceeds the maximum 480p limit."
                    )
                )
            }

            // 3. Validate FPS <= 60 FPS
            if (detectedFps > MAX_FPS) {
                return Result.failure(
                    IllegalArgumentException(
                        "Video frame rate ($detectedFps FPS) exceeds the maximum 60 FPS limit."
                    )
                )
            }

            // Read video bytes
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalArgumentException("Unable to read video file data."))

            if (rawBytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("Selected video file is empty."))
            }

            if (rawBytes.size > MAX_FIRESTORE_VIDEO_BYTES) {
                val sizeMb = String.format("%.1f", rawBytes.size / (1024f * 1024f))
                return Result.failure(
                    IllegalArgumentException(
                        "Video file size (${sizeMb} MB) is too large for direct Firestore document chunking (max 12 MB). Please use a higher-compression 480p clip."
                    )
                )
            }

            // Extract representative thumbnail frame for fast feed preview
            val frameBitmap = retriever.getFrameAtTime(
                minOf(durationMs * 1000L / 4L, 1_000_000L),
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.getFrameAtTime(0L)

            val scaledThumbnail: Bitmap? = frameBitmap?.let { bmp ->
                val maxDim = 640f
                val scale = minOf(1f, maxDim / maxOf(bmp.width, bmp.height).toFloat())
                val targetW = maxOf(1, (bmp.width * scale).roundToInt())
                val targetH = maxOf(1, (bmp.height * scale).roundToInt())
                Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
            }

            val thumbnailBase64 = if (scaledThumbnail != null) {
                val out = ByteArrayOutputStream()
                scaledThumbnail.compress(Bitmap.CompressFormat.JPEG, 78, out)
                "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            } else {
                ""
            }

            Result.success(
                VideoValidationSpec(
                    uri = uri,
                    durationMs = durationMs,
                    durationSeconds = durationSeconds,
                    width = width,
                    height = height,
                    fps = detectedFps,
                    fileSizeBytes = rawBytes.size.toLong(),
                    thumbnailBitmap = scaledThumbnail,
                    thumbnailBase64 = thumbnailBase64,
                    rawBytes = rawBytes
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException(e.localizedMessage ?: "Failed to validate video file.")
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun extractFrameRate(
        context: Context,
        uri: Uri,
        retriever: MediaMetadataRetriever,
        durationMs: Long
    ): Int {
        // 1. Try MediaExtractor track format KEY_FRAME_RATE
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        val fps = try {
                            format.getInteger(MediaFormat.KEY_FRAME_RATE)
                        } catch (_: Exception) {
                            format.getFloat(MediaFormat.KEY_FRAME_RATE).roundToInt()
                        }
                        if (fps > 0) return fps
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }

        // 2. Fallback: METADATA_KEY_CAPTURE_FRAMERATE
        val captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()?.roundToInt()
        if (captureFps != null && captureFps > 0) {
            return captureFps
        }

        // 3. Fallback: Total frame count / durationSeconds
        val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            ?.toLongOrNull()
        if (frameCount != null && frameCount > 0 && durationMs > 0) {
            val computed = (frameCount * 1000.0 / durationMs.toDouble()).roundToInt()
            if (computed in 1..240) return computed
        }

        // Default standard frame rate when container omits explicit header
        return 30
    }
}
