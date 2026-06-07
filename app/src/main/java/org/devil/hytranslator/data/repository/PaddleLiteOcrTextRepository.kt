package org.devil.hytranslator.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.devil.hytranslator.platform.ocr.OcrTextRepository
import java.io.File
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class PaddleLiteOcrTextRepository(
    private val context: Context,
    private val runtime: PaddleLiteOcrRuntime = PaddleLiteOcrRuntime(context),
) : OcrTextRepository {
    private var session: PaddleLiteOcrSession? = null

    override suspend fun recognize(bitmap: Bitmap): String {
        val activeSession = session ?: runtime.createSession().also { session = it }
        return withContext(Dispatchers.Default) {
            activeSession.recognize(bitmap)
        }
    }

    override suspend fun recognize(uri: Uri, decodeFailedMessage: String): String {
        val bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val raw = BitmapFactory.decodeStream(inputStream)
                raw ?: throw OcrProcessingException(decodeFailedMessage)
            }
                ?: throw OcrProcessingException(decodeFailedMessage)
        }
        val corrected = withContext(Dispatchers.IO) {
            val orientation = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: return@withContext bitmap
            rotateBitmap(bitmap, orientation)
        }
        return recognize(corrected)
    }

    override fun close() {
        session = null
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

class PaddleLiteOcrRuntime(
    filesDir: File,
    private val predictorFactory: (MobileConfig) -> PaddleLitePredictorHandle = { config ->
        DefaultPaddleLitePredictorHandle(PaddlePredictor.createPaddlePredictor(config))
    },
) {
    constructor(
        context: Context,
        predictorFactory: (MobileConfig) -> PaddleLitePredictorHandle = { config ->
            DefaultPaddleLitePredictorHandle(PaddlePredictor.createPaddlePredictor(config))
        },
    ) : this(context.filesDir, predictorFactory)

    private val assetDir = File(filesDir, "ai-assets/pp-ocrv5-mobile")

    fun createSession(): PaddleLiteOcrSession {
        val detModel = requiredFile("PP-OCRv5_mobile_det.nb")
        val recModel = requiredFile("PP-OCRv5_mobile_rec.nb")
        val labels = requiredFile("ppocr_keys_ocrv5.txt")

        val detPredictor = createPredictor(detModel)
        val recPredictor = createPredictor(recModel)
        val labelLines = labels.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
        require(labelLines.isNotEmpty()) {
            "PaddleOCR label file is empty"
        }

        return PaddleLiteOcrSession(detPredictor, recPredictor, labelLines)
    }

    private fun requiredFile(name: String): File {
        val file = assetDir.resolve(name)
        require(file.isFile) {
            "Missing PaddleOCR file: $name"
        }
        return file
    }

    private fun createPredictor(model: File): PaddleLitePredictorHandle {
        val config = MobileConfig().apply {
            setModelFromFile(model.absolutePath)
            setThreads(2)
            setPowerMode(PowerMode.LITE_POWER_HIGH)
        }
        return predictorFactory(config)
    }
}

class PaddleLiteOcrSession(
    private val detPredictor: PaddleLitePredictorHandle,
    private val recPredictor: PaddleLitePredictorHandle,
    val labels: List<String>,
) {
    private val characterDictionary = buildList {
        add("#")
        addAll(labels)
        add(" ")
        add("")
    }

    fun ensureReady() {
        detPredictor.version()
        recPredictor.version()
    }

    fun recognizeSingleLine(bitmap: Bitmap): String {
        ensureReady()
        return recognizeBitmapLine(bitmap)
    }

    fun recognize(bitmap: Bitmap): String {
        ensureReady()
        val input = PaddleOcrDetectionPreprocessor.createInput(bitmap)
        detPredictor.setInput(input.shape, input.data)
        check(detPredictor.run()) {
            "PaddleOCR detection predictor failed"
        }

        val boxes = PaddleOcrDetectionPostprocessor.detectTextBoxes(
            probabilities = detPredictor.outputFloatData(),
            shape = detPredictor.outputShape(),
            resize = input.resize,
        )
        if (boxes.isEmpty()) return ""

        return PaddleOcrDetectionPostprocessor.sortForReading(boxes)
            .mapNotNull { box ->
                val crop = bitmap.crop(box)
                val text = recognizeBitmapLine(crop)
                text.takeIf { it.isNotBlank() }
            }
            .joinToString(separator = "\n")
    }

    private fun recognizeBitmapLine(bitmap: Bitmap): String {
        val input = PaddleOcrRecognitionPreprocessor.createInput(bitmap)
        recPredictor.setInput(input.shape, input.data)
        check(recPredictor.run()) {
            "PaddleOCR recognition predictor failed"
        }
        val decoded = PaddleOcrCtcDecoder.decode(
            probabilities = recPredictor.outputFloatData(),
            shape = recPredictor.outputShape(),
            dictionary = characterDictionary,
        )
        return decoded.text
    }

    private fun Bitmap.crop(box: PaddleOcrTextBox): Bitmap {
        val paddedBox = box.expandForRecognition(sourceWidth = width, sourceHeight = height)
        if (paddedBox.isAxisAligned) {
            val left = paddedBox.left.coerceIn(0, width - 1)
            val top = paddedBox.top.coerceIn(0, height - 1)
            val right = paddedBox.right.coerceIn(left + 1, width)
            val bottom = paddedBox.bottom.coerceIn(top + 1, height)
            return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
        }

        val targetWidth = paddedBox.edgeWidth.coerceAtLeast(1)
        val targetHeight = paddedBox.edgeHeight.coerceAtLeast(1)
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val src = paddedBox.points.flatMap { point ->
            listOf(
                point.x.coerceIn(0f, (width - 1).toFloat()),
                point.y.coerceIn(0f, (height - 1).toFloat()),
            )
        }.toFloatArray()
        val dst = floatArrayOf(
            0f,
            0f,
            targetWidth.toFloat(),
            0f,
            targetWidth.toFloat(),
            targetHeight.toFloat(),
            0f,
            targetHeight.toFloat(),
        )
        val matrix = Matrix().apply {
            check(setPolyToPoly(src, 0, dst, 0, 4)) {
                "PaddleOCR perspective crop matrix failed"
            }
        }
        Canvas(output).drawBitmap(this, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }
}

interface PaddleLitePredictorHandle {
    fun version(): String
    fun setInput(shape: LongArray, data: FloatArray)
    fun run(): Boolean
    fun outputShape(): LongArray
    fun outputFloatData(): FloatArray
}

private class DefaultPaddleLitePredictorHandle(
    private val predictor: PaddlePredictor,
) : PaddleLitePredictorHandle {
    override fun version(): String = predictor.getVersion()
    override fun setInput(shape: LongArray, data: FloatArray) {
        val input = predictor.getInput(0)
        input.resize(shape)
        input.setData(data)
    }

    override fun run(): Boolean = predictor.run()

    override fun outputShape(): LongArray = predictor.getOutput(0).shape()

    override fun outputFloatData(): FloatArray = predictor.getOutput(0).floatData
}

data class PaddleOcrDecodeResult(
    val text: String,
    val score: Float,
)

data class PaddleOcrRecognitionInput(
    val shape: LongArray,
    val data: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaddleOcrRecognitionInput) return false
        return shape.contentEquals(other.shape) && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = shape.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

object PaddleOcrRecognitionPreprocessor {
    fun createInput(bitmap: Bitmap): PaddleOcrRecognitionInput {
        require(bitmap.width > 0 && bitmap.height > 0) {
            "PaddleOCR input bitmap is empty"
        }
        val resizedHeight = 32
        val widthRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val resizedWidth = ceil(resizedHeight * widthRatio).toInt().coerceAtLeast(1)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, true)
        val pixels = IntArray(resizedWidth * resizedHeight)
        resizedBitmap.getPixels(pixels, 0, resizedWidth, 0, 0, resizedWidth, resizedHeight)

        val planeSize = resizedWidth * resizedHeight
        val data = FloatArray(3 * planeSize)
        pixels.forEachIndexed { index, pixel ->
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            data[index] = normalize(blue)
            data[planeSize + index] = normalize(green)
            data[planeSize * 2 + index] = normalize(red)
        }
        return PaddleOcrRecognitionInput(
            shape = longArrayOf(1, 3, resizedHeight.toLong(), resizedWidth.toLong()),
            data = data,
        )
    }

    private fun normalize(channel: Int): Float = (channel / 255f - 0.5f) / 0.5f
}

data class PaddleOcrDetectionResize(
    val width: Int,
    val height: Int,
    val ratioWidth: Float,
    val ratioHeight: Float,
)

data class PaddleOcrDetectionInput(
    val shape: LongArray,
    val data: FloatArray,
    val resize: PaddleOcrDetectionResize,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaddleOcrDetectionInput) return false
        return shape.contentEquals(other.shape) &&
            data.contentEquals(other.data) &&
            resize == other.resize
    }

    override fun hashCode(): Int {
        var result = shape.contentHashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + resize.hashCode()
        return result
    }
}

object PaddleOcrDetectionPreprocessor {
    fun createInput(
        bitmap: Bitmap,
        maxSideLength: Int = DEFAULT_MAX_SIDE_LENGTH,
    ): PaddleOcrDetectionInput {
        val resize = resizePlan(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            maxSideLength = maxSideLength,
        )
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, resize.width, resize.height, true)
        val pixels = IntArray(resize.width * resize.height)
        resizedBitmap.getPixels(pixels, 0, resize.width, 0, 0, resize.width, resize.height)
        return createInputFromPixels(resize, pixels)
    }

    fun createInputFromPixels(
        resize: PaddleOcrDetectionResize,
        pixels: IntArray,
    ): PaddleOcrDetectionInput {
        require(pixels.size >= resize.width * resize.height) {
            "PaddleOCR detection pixel buffer is shorter than resize shape"
        }

        val planeSize = resize.width * resize.height
        val data = FloatArray(3 * planeSize)
        for (index in 0 until planeSize) {
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            data[index] = normalize(blue, DET_MEAN_BLUE, DET_STD_BLUE)
            data[planeSize + index] = normalize(green, DET_MEAN_GREEN, DET_STD_GREEN)
            data[planeSize * 2 + index] = normalize(red, DET_MEAN_RED, DET_STD_RED)
        }

        return PaddleOcrDetectionInput(
            shape = longArrayOf(1, 3, resize.height.toLong(), resize.width.toLong()),
            data = data,
            resize = resize,
        )
    }

    fun resizePlan(
        sourceWidth: Int,
        sourceHeight: Int,
        maxSideLength: Int = DEFAULT_MAX_SIDE_LENGTH,
    ): PaddleOcrDetectionResize {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "PaddleOCR detection input bitmap is empty"
        }
        require(maxSideLength >= MODEL_STRIDE) {
            "PaddleOCR detection max side must be at least $MODEL_STRIDE"
        }

        val scale = if (sourceWidth > sourceHeight) {
            maxSideLength.toFloat() / sourceWidth.toFloat()
        } else {
            maxSideLength.toFloat() / sourceHeight.toFloat()
        }.coerceAtMost(1f)

        val resizedWidth = alignToStride((sourceWidth * scale).roundToInt())
        val resizedHeight = alignToStride((sourceHeight * scale).roundToInt())

        return PaddleOcrDetectionResize(
            width = resizedWidth,
            height = resizedHeight,
            ratioWidth = resizedWidth.toFloat() / sourceWidth.toFloat(),
            ratioHeight = resizedHeight.toFloat() / sourceHeight.toFloat(),
        )
    }

    private fun alignToStride(value: Int): Int =
        value
            .coerceAtLeast(MODEL_STRIDE)
            .let { aligned -> ((aligned + MODEL_STRIDE / 2) / MODEL_STRIDE) * MODEL_STRIDE }
            .coerceAtLeast(MODEL_STRIDE)

    private fun normalize(channel: Int, mean: Float, std: Float): Float =
        (channel / 255f - mean) / std

    private const val DEFAULT_MAX_SIDE_LENGTH = 960
    private const val MODEL_STRIDE = 32
    private const val DET_MEAN_BLUE = 0.406f
    private const val DET_MEAN_GREEN = 0.456f
    private const val DET_MEAN_RED = 0.485f
    private const val DET_STD_BLUE = 0.225f
    private const val DET_STD_GREEN = 0.224f
    private const val DET_STD_RED = 0.229f
}

data class PaddleOcrPoint(
    val x: Float,
    val y: Float,
)

data class PaddleOcrTextBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val score: Float,
    val points: List<PaddleOcrPoint> = listOf(
        PaddleOcrPoint(left.toFloat(), top.toFloat()),
        PaddleOcrPoint(right.toFloat(), top.toFloat()),
        PaddleOcrPoint(right.toFloat(), bottom.toFloat()),
        PaddleOcrPoint(left.toFloat(), bottom.toFloat()),
    ),
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Int get() = width * height
    val isAxisAligned: Boolean
        get() = points.size != 4 ||
            points[0].y == points[1].y &&
            points[1].x == points[2].x &&
            points[2].y == points[3].y &&
            points[3].x == points[0].x
    val edgeWidth: Int
        get() = max(
            points[0].distanceTo(points[1]).roundToInt(),
            points[2].distanceTo(points[3]).roundToInt(),
        )
    val edgeHeight: Int
        get() = max(
            points[1].distanceTo(points[2]).roundToInt(),
            points[3].distanceTo(points[0]).roundToInt(),
        )

    fun expandForRecognition(
        sourceWidth: Int,
        sourceHeight: Int,
        horizontalRatio: Float = DEFAULT_RECOGNITION_HORIZONTAL_PADDING_RATIO,
        verticalRatio: Float = DEFAULT_RECOGNITION_VERTICAL_PADDING_RATIO,
    ): PaddleOcrTextBox {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "PaddleOCR crop source size must be positive"
        }
        require(horizontalRatio >= 0f && verticalRatio >= 0f) {
            "PaddleOCR crop padding ratio must be non-negative"
        }

        val horizontalPadding = (width * horizontalRatio).roundToInt().coerceAtLeast(2)
        val verticalPadding = (height * verticalRatio).roundToInt().coerceAtLeast(2)
        val centerX = points.map { it.x }.average().toFloat()
        val centerY = points.map { it.y }.average().toFloat()
        val expandedPoints = points.map { point ->
            val dx = point.x - centerX
            val dy = point.y - centerY
            val length = hypot(dx, dy).coerceAtLeast(1f)
            PaddleOcrPoint(
                x = (point.x + dx / length * horizontalPadding)
                    .coerceIn(0f, sourceWidth.toFloat()),
                y = (point.y + dy / length * verticalPadding)
                    .coerceIn(0f, sourceHeight.toFloat()),
            )
        }
        return copy(
            left = (left - horizontalPadding).coerceAtLeast(0),
            top = (top - verticalPadding).coerceAtLeast(0),
            right = (right + horizontalPadding).coerceAtMost(sourceWidth),
            bottom = (bottom + verticalPadding).coerceAtMost(sourceHeight),
            points = expandedPoints,
        )
    }

    private companion object {
        const val DEFAULT_RECOGNITION_HORIZONTAL_PADDING_RATIO = 0.08f
        const val DEFAULT_RECOGNITION_VERTICAL_PADDING_RATIO = 0.15f
    }
}

private fun PaddleOcrPoint.distanceTo(other: PaddleOcrPoint): Float =
    hypot(x - other.x, y - other.y)

object PaddleOcrDetectionPostprocessor {
    fun detectTextBoxes(
        probabilities: FloatArray,
        shape: LongArray,
        resize: PaddleOcrDetectionResize,
        threshold: Float = DEFAULT_THRESHOLD,
        minArea: Int = DEFAULT_MIN_AREA,
    ): List<PaddleOcrTextBox> {
        require(shape.size >= 4) {
            "PaddleOCR detection output shape must have at least 4 dimensions"
        }
        val height = shape[shape.size - 2].toInt()
        val width = shape[shape.size - 1].toInt()
        require(width > 0 && height > 0) {
            "PaddleOCR detection output shape is empty"
        }
        require(probabilities.size >= width * height) {
            "PaddleOCR detection output data is shorter than its shape"
        }
        require(threshold in 0f..1f) {
            "PaddleOCR detection threshold must be between 0 and 1"
        }

        val visited = BooleanArray(width * height)
        val boxes = mutableListOf<PaddleOcrTextBox>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (visited[index] || probabilities[index] < threshold) continue
                val component = floodFill(
                    probabilities = probabilities,
                    visited = visited,
                    width = width,
                    height = height,
                    startX = x,
                    startY = y,
                    threshold = threshold,
                )
                val box = component.toSourceBox(
                    resize = resize,
                    unclip = ::unclip,
                    minimumAreaRect = ::minimumAreaRect,
                )
                if (box.area >= minArea) {
                    boxes += box
                }
            }
        }
        return boxes
    }

    fun sortForReading(boxes: List<PaddleOcrTextBox>): List<PaddleOcrTextBox> =
        boxes.sortedWith(
            compareBy<PaddleOcrTextBox> { box -> box.top }
                .thenBy { box -> box.left },
        )

    private fun floodFill(
        probabilities: FloatArray,
        visited: BooleanArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        threshold: Float,
    ): DetectionComponent {
        val queue = ArrayDeque<Int>()
        queue.add(startY * width + startX)

        var left = startX
        var top = startY
        var right = startX
        var bottom = startY
        var score = 0f
        var count = 0
        val contour = mutableListOf<PaddleOcrPoint>()

        while (queue.isNotEmpty()) {
            val encoded = queue.removeFirst()
            val x = encoded % width
            val y = encoded / width

            if (x !in 0 until width || y !in 0 until height) continue
            val index = y * width + x
            if (visited[index] || probabilities[index] < threshold) continue
            visited[index] = true

            left = min(left, x)
            top = min(top, y)
            right = max(right, x)
            bottom = max(bottom, y)
            score += probabilities[index]
            count += 1
            if (isBoundaryPixel(probabilities, width, height, x, y, threshold)) {
                contour += PaddleOcrPoint(x.toFloat(), y.toFloat())
                contour += PaddleOcrPoint((x + 1).toFloat(), y.toFloat())
                contour += PaddleOcrPoint((x + 1).toFloat(), (y + 1).toFloat())
                contour += PaddleOcrPoint(x.toFloat(), (y + 1).toFloat())
            }

            if (x > 0) queue.add(y * width + x - 1)
            if (x < width - 1) queue.add(y * width + x + 1)
            if (y > 0) queue.add((y - 1) * width + x)
            if (y < height - 1) queue.add((y + 1) * width + x)
        }

        return DetectionComponent(
            left = left,
            top = top,
            right = right + 1,
            bottom = bottom + 1,
            score = if (count == 0) 0f else score / count,
            contour = contour,
        )
    }

    private fun isBoundaryPixel(
        probabilities: FloatArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        threshold: Float,
    ): Boolean {
        val neighbors = intArrayOf(-1, 0, 1, 0, 0, -1, 0, 1)
        for (index in neighbors.indices step 2) {
            val nextX = x + neighbors[index]
            val nextY = y + neighbors[index + 1]
            if (nextX !in 0 until width || nextY !in 0 until height) return true
            if (probabilities[nextY * width + nextX] < threshold) return true
        }
        return false
    }

    private fun unclip(points: List<PaddleOcrPoint>): List<PaddleOcrPoint> {
        val hull = convexHull(points)
        if (hull.size < MIN_CONTOUR_POINTS) return points
        val centerX = hull.map { it.x }.average().toFloat()
        val centerY = hull.map { it.y }.average().toFloat()
        val distance = polygonArea(hull) * UNCLIP_RATIO / polygonPerimeter(hull).coerceAtLeast(1f)
        return hull.map { point ->
            val dx = point.x - centerX
            val dy = point.y - centerY
            val length = hypot(dx, dy).coerceAtLeast(1f)
            PaddleOcrPoint(
                x = point.x + dx / length * distance,
                y = point.y + dy / length * distance,
            )
        }
    }

    private fun minimumAreaRect(points: List<PaddleOcrPoint>): List<PaddleOcrPoint> {
        val hull = convexHull(points)
        if (hull.size < MIN_CONTOUR_POINTS) return points

        var best: RotatedRect? = null
        for (index in hull.indices) {
            val start = hull[index]
            val end = hull[(index + 1) % hull.size]
            val angle = atan2(end.y - start.y, end.x - start.x)
            val axisX = cos(angle)
            val axisY = sin(angle)
            val normalX = -axisY
            val normalY = axisX
            var minAxis = Float.POSITIVE_INFINITY
            var maxAxis = Float.NEGATIVE_INFINITY
            var minNormal = Float.POSITIVE_INFINITY
            var maxNormal = Float.NEGATIVE_INFINITY
            hull.forEach { point ->
                val projectedAxis = point.x * axisX + point.y * axisY
                val projectedNormal = point.x * normalX + point.y * normalY
                minAxis = min(minAxis, projectedAxis)
                maxAxis = max(maxAxis, projectedAxis)
                minNormal = min(minNormal, projectedNormal)
                maxNormal = max(maxNormal, projectedNormal)
            }
            val area = (maxAxis - minAxis) * (maxNormal - minNormal)
            if (best == null || area < best.area) {
                best = RotatedRect(
                    axisX = axisX,
                    axisY = axisY,
                    normalX = normalX,
                    normalY = normalY,
                    minAxis = minAxis,
                    maxAxis = maxAxis,
                    minNormal = minNormal,
                    maxNormal = maxNormal,
                    area = area,
                )
            }
        }
        return best?.toPoints()?.orderClockwiseFromTopLeft().orEmpty()
    }

    private fun convexHull(points: List<PaddleOcrPoint>): List<PaddleOcrPoint> {
        val sorted = points
            .distinct()
            .sortedWith(compareBy<PaddleOcrPoint> { it.x }.thenBy { it.y })
        if (sorted.size <= 2) return sorted

        val lower = mutableListOf<PaddleOcrPoint>()
        sorted.forEach { point ->
            while (lower.size >= 2 &&
                cross(lower[lower.lastIndex - 1], lower.last(), point) <= 0f
            ) {
                lower.removeAt(lower.lastIndex)
            }
            lower += point
        }

        val upper = mutableListOf<PaddleOcrPoint>()
        sorted.asReversed().forEach { point ->
            while (upper.size >= 2 &&
                cross(upper[upper.lastIndex - 1], upper.last(), point) <= 0f
            ) {
                upper.removeAt(upper.lastIndex)
            }
            upper += point
        }

        return (lower.dropLast(1) + upper.dropLast(1))
    }

    private fun cross(
        origin: PaddleOcrPoint,
        a: PaddleOcrPoint,
        b: PaddleOcrPoint,
    ): Float = (a.x - origin.x) * (b.y - origin.y) - (a.y - origin.y) * (b.x - origin.x)

    private fun polygonArea(points: List<PaddleOcrPoint>): Float {
        if (points.size < MIN_CONTOUR_POINTS) return 0f
        var area = 0f
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            area += current.x * next.y - next.x * current.y
        }
        return abs(area) / 2f
    }

    private fun polygonPerimeter(points: List<PaddleOcrPoint>): Float {
        if (points.size < 2) return 0f
        var perimeter = 0f
        for (index in points.indices) {
            perimeter += points[index].distanceTo(points[(index + 1) % points.size])
        }
        return perimeter
    }

    private data class RotatedRect(
        val axisX: Float,
        val axisY: Float,
        val normalX: Float,
        val normalY: Float,
        val minAxis: Float,
        val maxAxis: Float,
        val minNormal: Float,
        val maxNormal: Float,
        val area: Float,
    ) {
        fun toPoints(): List<PaddleOcrPoint> =
            listOf(
                toPoint(minAxis, minNormal),
                toPoint(maxAxis, minNormal),
                toPoint(maxAxis, maxNormal),
                toPoint(minAxis, maxNormal),
            )

        private fun toPoint(axis: Float, normal: Float): PaddleOcrPoint =
            PaddleOcrPoint(
                x = axis * axisX + normal * normalX,
                y = axis * axisY + normal * normalY,
            )
    }

    private fun List<PaddleOcrPoint>.orderClockwiseFromTopLeft(): List<PaddleOcrPoint> {
        val centerX = map { it.x }.average().toFloat()
        val centerY = map { it.y }.average().toFloat()
        val ordered = sortedBy { point -> atan2(point.y - centerY, point.x - centerX) }
        val topLeftIndex = ordered.indices.minBy { index ->
            ordered[index].x + ordered[index].y
        }
        return ordered.drop(topLeftIndex) + ordered.take(topLeftIndex)
    }

    private data class DetectionComponent(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val score: Float,
        val contour: List<PaddleOcrPoint>,
    ) {
        fun toSourceBox(
            resize: PaddleOcrDetectionResize,
            unclip: (List<PaddleOcrPoint>) -> List<PaddleOcrPoint>,
            minimumAreaRect: (List<PaddleOcrPoint>) -> List<PaddleOcrPoint>,
        ): PaddleOcrTextBox {
            val rectPoints = if (contour.size >= MIN_CONTOUR_POINTS) {
                minimumAreaRect(unclip(contour))
            } else {
                listOf(
                    PaddleOcrPoint(left.toFloat(), top.toFloat()),
                    PaddleOcrPoint(right.toFloat(), top.toFloat()),
                    PaddleOcrPoint(right.toFloat(), bottom.toFloat()),
                    PaddleOcrPoint(left.toFloat(), bottom.toFloat()),
                )
            }.map { point ->
                PaddleOcrPoint(
                    x = point.x / resize.ratioWidth,
                    y = point.y / resize.ratioHeight,
                )
            }
            val sourceLeft = rectPoints.minOf { it.x }.roundToInt()
            val sourceTop = rectPoints.minOf { it.y }.roundToInt()
            val sourceRight = rectPoints.maxOf { it.x }.roundToInt()
            val sourceBottom = rectPoints.maxOf { it.y }.roundToInt()
            return PaddleOcrTextBox(
                left = sourceLeft,
                top = sourceTop,
                right = sourceRight,
                bottom = sourceBottom,
                score = score,
                points = rectPoints,
            )
        }
    }

    private const val DEFAULT_THRESHOLD = 0.3f
    private const val DEFAULT_MIN_AREA = 16
    private const val MIN_CONTOUR_POINTS = 3
    private const val UNCLIP_RATIO = 1.5f
}

object PaddleOcrCtcDecoder {
    fun decode(
        probabilities: FloatArray,
        shape: LongArray,
        dictionary: List<String>,
    ): PaddleOcrDecodeResult {
        require(shape.size >= 3) {
            "PaddleOCR recognition output shape must have at least 3 dimensions"
        }
        val steps = shape[1].toInt()
        val classes = shape[2].toInt()
        require(steps > 0 && classes > 0) {
            "PaddleOCR recognition output shape is empty"
        }
        require(probabilities.size >= steps * classes) {
            "PaddleOCR recognition output data is shorter than its shape"
        }
        require(dictionary.size >= classes) {
            "PaddleOCR label dictionary size ${dictionary.size} is smaller than " +
                "recognition output classes $classes for shape ${shape.toList()}"
        }

        val text = StringBuilder()
        var lastIndex = 0
        var score = 0f
        var count = 0
        for (step in 0 until steps) {
            val offset = step * classes
            var maxIndex = 0
            var maxValue = probabilities[offset]
            for (index in 1 until classes) {
                val value = probabilities[offset + index]
                if (value > maxValue) {
                    maxValue = value
                    maxIndex = index
                }
            }
            if (maxIndex > 0 && maxIndex != lastIndex) {
                text.append(dictionary[maxIndex])
                score += maxValue
                count += 1
            }
            lastIndex = maxIndex
        }
        return PaddleOcrDecodeResult(
            text = text.toString(),
            score = if (count == 0) 0f else score / count,
        )
    }
}
