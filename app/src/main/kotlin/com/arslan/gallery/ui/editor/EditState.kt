package com.arslan.gallery.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class EditorTool { Crop, Filters, Adjust, Draw }

enum class Adjustment { Brightness, Contrast, Saturation, Warmth }

/** Color filter preset, applied below the adjustments. */
enum class Filter(val matrix: () -> ColorMatrix) {
    Original({ ColorMatrix() }),
    Vivid({ ColorMatrix().apply { setSaturation(1.45f) } }),
    Warm({ scale(1.08f, 1f, 0.88f) }),
    Cool({ scale(0.9f, 1f, 1.1f) }),
    Mono({ ColorMatrix().apply { setSaturation(0f) } }),
    Noir({ ColorMatrix().apply { setSaturation(0f) }.also { it.postConcat(contrast(1.35f)) } }),
    Sepia({
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }),
    Fade({ contrast(0.8f).also { it.postConcat(ColorMatrix().apply { setSaturation(0.75f) }) } }),
    Chrome({ contrast(1.15f).also { it.postConcat(ColorMatrix().apply { setSaturation(1.2f) }) } }),
}

private fun scale(r: Float, g: Float, b: Float) = ColorMatrix().apply { setScale(r, g, b, 1f) }

private fun contrast(value: Float): ColorMatrix {
    val translate = (1f - value) * 128f
    return ColorMatrix(
        floatArrayOf(
            value, 0f, 0f, 0f, translate,
            0f, value, 0f, 0f, translate,
            0f, 0f, value, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        )
    )
}

/** Aspect ratio of the crop, 0 means free and -1 the original ratio. */
enum class CropRatio(val ratio: Float, val label: String?) {
    Free(0f, null),
    Original(-1f, null),
    Square(1f, "1:1"),
    FourThree(4f / 3f, "4:3"),
    ThreeFour(3f / 4f, "3:4"),
    SixteenNine(16f / 9f, "16:9"),
    NineSixteen(9f / 16f, "9:16"),
}

/** A brush stroke, in pixels of the source bitmap so it stays put when the image is cropped or rotated. */
class Stroke(val path: Path, val color: Int, val width: Float)

/**
 * Edits applied to [source]: rotation and mirroring, a crop in the coordinates of the rotated image
 * (normalized to 0..1), a filter, adjustments and drawn strokes. Nothing touches the bitmap until
 * [render] produces the result.
 */
@Stable
class EditState(val source: Bitmap) {
    var tool by mutableStateOf(EditorTool.Crop)
    var rotation by mutableIntStateOf(0)
        private set
    var flipped by mutableStateOf(false)
        private set
    var crop by mutableStateOf(RectF(0f, 0f, 1f, 1f))
    var cropRatio by mutableStateOf(CropRatio.Free)
        private set
    var filter by mutableStateOf(Filter.Original)
    var brightness by mutableFloatStateOf(0f)
    var contrast by mutableFloatStateOf(0f)
    var saturation by mutableFloatStateOf(0f)
    var warmth by mutableFloatStateOf(0f)
    val strokes = mutableStateListOf<Stroke>()

    /** Brush width in dp, on screen. */
    var brushSize by mutableFloatStateOf(8f)

    /** Bumped while a stroke is drawn, so the canvas redraws the live path. */
    var strokeVersion by mutableIntStateOf(0)

    val hasChanges
        get() = rotation != 0 || flipped || crop != RectF(0f, 0f, 1f, 1f) || filter != Filter.Original ||
            brightness != 0f || contrast != 0f || saturation != 0f || warmth != 0f || strokes.isNotEmpty()

    /** Size of the image after rotation. */
    val rotatedWidth get() = if (rotation % 180 == 0) source.width else source.height
    val rotatedHeight get() = if (rotation % 180 == 0) source.height else source.width

    fun rotate(degrees: Int) {
        // Keep the same part of the image selected, the crop rotates together with the image.
        val c = crop
        crop = if ((degrees + 360) % 360 == 90) {
            RectF(1f - c.bottom, c.left, 1f - c.top, c.right)
        } else {
            RectF(c.top, 1f - c.right, c.bottom, 1f - c.left)
        }
        // Mirroring happens after rotating, so a mirrored image has to rotate the other way around.
        rotation = (rotation + (if (flipped) -degrees else degrees) + 360) % 360
        if (cropRatio.ratio > 0f) setRatio(cropRatio.flipped())
    }

    fun flip() {
        flipped = !flipped
        val c = crop
        crop = RectF(1f - c.right, c.top, 1f - c.left, c.bottom)
    }

    fun resetGeometry() {
        rotation = 0
        flipped = false
        cropRatio = CropRatio.Free
        crop = RectF(0f, 0f, 1f, 1f)
    }

    /** Applies [ratio] with the biggest centered crop that fits. */
    fun setRatio(ratio: CropRatio) {
        cropRatio = ratio
        val target = targetRatio() ?: return
        val imageRatio = rotatedWidth.toFloat() / rotatedHeight
        // Size of the crop relative to the image, as large as possible.
        val (w, h) = if (target > imageRatio) 1f to imageRatio / target else target / imageRatio to 1f
        val cx = crop.centerX().coerceIn(w / 2, 1 - w / 2)
        val cy = crop.centerY().coerceIn(h / 2, 1 - h / 2)
        crop = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    /** Crop ratio in pixels (width / height), null when free. */
    fun targetRatio(): Float? = when {
        cropRatio == CropRatio.Free -> null
        cropRatio == CropRatio.Original -> source.width.toFloat() / source.height
        else -> cropRatio.ratio
    }

    private fun CropRatio.flipped() = when (this) {
        CropRatio.FourThree -> CropRatio.ThreeFour
        CropRatio.ThreeFour -> CropRatio.FourThree
        CropRatio.SixteenNine -> CropRatio.NineSixteen
        CropRatio.NineSixteen -> CropRatio.SixteenNine
        else -> this
    }

    fun adjustment(type: Adjustment) = when (type) {
        Adjustment.Brightness -> brightness
        Adjustment.Contrast -> contrast
        Adjustment.Saturation -> saturation
        Adjustment.Warmth -> warmth
    }

    fun setAdjustment(type: Adjustment, value: Float) {
        when (type) {
            Adjustment.Brightness -> brightness = value
            Adjustment.Contrast -> contrast = value
            Adjustment.Saturation -> saturation = value
            Adjustment.Warmth -> warmth = value
        }
    }

    /** Filter and adjustments as one color matrix. */
    fun colorMatrix(): ColorMatrix {
        val matrix = filter.matrix()
        if (saturation != 0f) matrix.postConcat(ColorMatrix().apply { setSaturation(1f + saturation) })
        if (contrast != 0f) matrix.postConcat(contrast(1f + contrast * 0.6f))
        if (warmth != 0f) matrix.postConcat(scale(1f + warmth * 0.12f, 1f, 1f - warmth * 0.12f))
        if (brightness != 0f) {
            val offset = brightness * 70f
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, offset,
                        0f, 1f, 0f, 0f, offset,
                        0f, 0f, 1f, 0f, offset,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            )
        }
        return matrix
    }

    /** Maps source pixels to pixels of the rotated (and mirrored) image. */
    fun sourceToRotated(): Matrix = Matrix().apply {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        setRotate(rotation.toFloat())
        when (rotation) {
            90 -> postTranslate(h, 0f)
            180 -> postTranslate(w, h)
            270 -> postTranslate(0f, w)
        }
        if (flipped) {
            postScale(-1f, 1f)
            postTranslate(rotatedWidth.toFloat(), 0f)
        }
    }

    /** Crop in pixels of the rotated image. */
    fun cropPixels() = RectF(
        crop.left * rotatedWidth,
        crop.top * rotatedHeight,
        crop.right * rotatedWidth,
        crop.bottom * rotatedHeight,
    )

    /**
     * Maps source pixels to a viewport of [viewWidth]x[viewHeight], fitting either the whole rotated
     * image or only the cropped part when [cropped]. [padding] keeps room for the crop handles.
     */
    fun viewMatrix(viewWidth: Float, viewHeight: Float, cropped: Boolean, padding: Float): Matrix {
        val region = if (cropped) cropPixels() else RectF(0f, 0f, rotatedWidth.toFloat(), rotatedHeight.toFloat())
        val scale = min((viewWidth - 2 * padding) / region.width(), (viewHeight - 2 * padding) / region.height())
        return sourceToRotated().apply {
            postTranslate(-region.left, -region.top)
            postScale(scale, scale)
            postTranslate((viewWidth - region.width() * scale) / 2, (viewHeight - region.height() * scale) / 2)
        }
    }

    /** The edited image, at most [maxSize] px on its longest side. */
    fun render(maxSize: Int = Int.MAX_VALUE): Bitmap {
        val region = cropPixels()
        val scale = min(1f, maxSize / max(region.width(), region.height()))
        val width = (region.width() * scale).roundToInt().coerceAtLeast(1)
        val height = (region.height() * scale).roundToInt().coerceAtLeast(1)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val matrix = sourceToRotated().apply {
            postTranslate(-region.left, -region.top)
            postScale(scale, scale)
        }
        Canvas(result).apply {
            drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix()) })
            concat(matrix)
            strokes.forEach { drawPath(it.path, strokePaint(it)) }
        }
        return result
    }

    companion object {
        fun strokePaint(stroke: Stroke) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = stroke.color
            strokeWidth = stroke.width
        }
    }
}
