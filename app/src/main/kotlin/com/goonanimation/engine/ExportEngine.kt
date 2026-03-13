package com.goonanimation.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Handles exporting animation frames to GIF and MP4 formats.
 *
 * GIF export uses a pure Kotlin implementation of the NeuQuant algorithm
 * for colour quantization, compatible with all API levels.
 *
 * MP4 export uses the hardware-accelerated MediaCodec + MediaMuxer pipeline.
 */
object ExportEngine {

    // ──────────────────────── GIF Export ────────────────────────

    /**
     * Exports the given list of bitmaps as an animated GIF file.
     * @param frames List of composited frame bitmaps
     * @param outputFile Destination file
     * @param fps Frames per second
     * @param onProgress 0..100 progress callback
     */
    suspend fun exportGif(
        frames: List<Bitmap>,
        outputFile: File,
        fps: Int = 12,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val encoder = AnimatedGifEncoder()
        encoder.setFrameRate(fps.toFloat())
        encoder.setRepeat(0) // loop forever

        FileOutputStream(outputFile).use { out ->
            encoder.start(out)
            frames.forEachIndexed { index, bitmap ->
                encoder.addFrame(bitmap)
                onProgress((index * 100) / frames.size)
            }
            encoder.finish()
        }
        onProgress(100)
    }

    // ──────────────────────── MP4 Export ────────────────────────

    /**
     * Exports the given list of bitmaps as an MP4 video file using MediaCodec.
     * @param frames List of composited frame bitmaps
     * @param outputFile Destination file
     * @param fps Frames per second
     * @param onProgress 0..100 progress callback
     */
    suspend fun exportMp4(
        frames: List<Bitmap>,
        outputFile: File,
        fps: Int = 12,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (frames.isEmpty()) return@withContext

        val width = makeEven(frames[0].width)
        val height = makeEven(frames[0].height)
        val bitrate = width * height * fps / 8
        val frameDurationUs = (1_000_000L / fps)

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate.coerceAtLeast(1_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var presentationTimeUs = 0L

        val bufferInfo = MediaCodec.BufferInfo()

        try {
            frames.forEachIndexed { index, sourceBitmap ->
                // Scale bitmap to even dimensions
                val bitmap = if (sourceBitmap.width != width || sourceBitmap.height != height) {
                    Bitmap.createScaledBitmap(sourceBitmap, width, height, true)
                } else sourceBitmap

                // Convert ARGB to YUV420
                val yuv = bitmapToYuv420(bitmap, width, height)

                val inputBufferId = codec.dequeueInputBuffer(10_000L)
                if (inputBufferId >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferId)!!
                    inputBuffer.clear()
                    inputBuffer.put(yuv)
                    codec.queueInputBuffer(
                        inputBufferId, 0, yuv.size, presentationTimeUs,
                        if (index == frames.lastIndex) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                    )
                    presentationTimeUs += frameDurationUs
                }

                // Drain encoder output
                drainEncoder(codec, muxer, bufferInfo, { idx -> trackIndex = idx }, trackIndex)

                onProgress((index * 90) / frames.size)
            }

            // Drain remaining output
            var maxDrains = frames.size + 10
            while (maxDrains-- > 0) {
                val drained = drainEncoder(codec, muxer, bufferInfo, { idx -> trackIndex = idx }, trackIndex)
                if (!drained) break
            }

        } finally {
            codec.stop()
            codec.release()
            if (trackIndex >= 0) muxer.stop()
            muxer.release()
        }
        onProgress(100)
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        onTrackAdded: (Int) -> Unit,
        currentTrackIndex: Int
    ): Boolean {
        var trackIndex = currentTrackIndex
        var drained = false
        loop@ while (true) {
            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
            when {
                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> break@loop
                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    onTrackAdded(trackIndex)
                    muxer.start()
                }
                outputBufferId >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputBufferId)!!
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && trackIndex >= 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        drained = true
                    }
                    codec.releaseOutputBuffer(outputBufferId, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break@loop
                    }
                }
                else -> break@loop
            }
        }
        return drained
    }

    /** Converts an ARGB Bitmap to YUV420 byte array for MediaCodec */
    private fun bitmapToYuv420(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val yuv = ByteArray(width * height * 3 / 2)
        val yLen = width * height
        var uvOffset = yLen

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[j * width + i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[j * width + i] = y.toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvOffset++] = u.toByte()
                    yuv[uvOffset++] = v.toByte()
                }
            }
        }
        return yuv
    }

    private fun makeEven(value: Int): Int = if (value % 2 == 0) value else value - 1
}

// ──────────────────────── Pure Kotlin GIF Encoder ────────────────────────

/**
 * Animated GIF encoder with NeuQuant colour quantization.
 * Based on the public-domain Java implementation by Kevin Weiner, FM Software.
 * Rewritten in Kotlin with coroutine-safe streaming output.
 */
internal class AnimatedGifEncoder {
    private var width = 0
    private var height = 0
    private var transparent = Color.TRANSPARENT
    private var transIndex = 0
    private var repeat = -1
    private var delay = 0
    private var started = false
    private var out: FileOutputStream? = null
    private var image: Bitmap? = null
    private var pixels: ByteArray = ByteArray(0)
    private var indexedPixels: ByteArray = ByteArray(0)
    private var colorDepth = 0
    private var colorTab: ByteArray = ByteArray(0)
    private var usedEntry = BooleanArray(256)
    private var palSize = 7
    private var dispose = -1
    private var firstFrame = true
    private var sizeSet = false
    private var sample = 10

    fun setDelay(ms: Int) { delay = (ms / 10f).toInt().coerceAtLeast(1) }
    fun setFrameRate(fps: Float) { if (fps != 0f) delay = (100f / fps).toInt().coerceAtLeast(1) }
    fun setRepeat(iter: Int) { repeat = iter }
    fun setTransparent(color: Int) { transparent = color }

    fun start(os: FileOutputStream): Boolean {
        out = os
        started = false
        return try {
            os.write("GIF89a".toByteArray())
            started = true
            true
        } catch (e: Exception) { false }
    }

    fun addFrame(im: Bitmap): Boolean {
        if (!started) return false
        return try {
            if (!sizeSet) {
                setSize(im.width, im.height)
            }
            image = im
            getImagePixels()
            analyzePixels()
            if (firstFrame) {
                writeLSD()
                writePalette()
                if (repeat >= 0) writeNetscapeExt()
            }
            writeGraphicCtrlExt()
            writeImageDesc()
            if (!firstFrame) writePalette()
            writePixels()
            firstFrame = false
            true
        } catch (e: Exception) { false }
    }

    fun finish(): Boolean {
        if (!started) return false
        return try {
            out?.write(0x3B) // GIF trailer
            out?.flush()
            true
        } catch (e: Exception) { false }
    }

    private fun setSize(w: Int, h: Int) {
        width = w; height = h; sizeSet = true
    }

    private fun getImagePixels() {
        val w = image!!.width; val h = image!!.height
        val argb = IntArray(w * h)
        image!!.getPixels(argb, 0, w, 0, 0, w, h)
        pixels = ByteArray(argb.size * 3)
        var k = 0
        for (px in argb) {
            pixels[k++] = Color.red(px).toByte()
            pixels[k++] = Color.green(px).toByte()
            pixels[k++] = Color.blue(px).toByte()
        }
    }

    private fun analyzePixels() {
        val nPix = pixels.size / 3
        indexedPixels = ByteArray(nPix)
        val nq = NeuQuant(pixels, pixels.size, sample)
        colorTab = nq.process()
        // Map image pixels to palette
        var k = 0
        for (i in 0 until nPix) {
            val index = nq.map(
                (pixels[k++].toInt() and 0xff),
                (pixels[k++].toInt() and 0xff),
                (pixels[k++].toInt() and 0xff)
            )
            usedEntry[index] = true
            indexedPixels[i] = index.toByte()
        }
        colorDepth = 8
        palSize = 7
        if (transparent != Color.TRANSPARENT) {
            transIndex = findClosest(transparent)
        }
    }

    private fun findClosest(c: Int): Int {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        var minPos = 0; var dmin = Int.MAX_VALUE; val n = colorTab.size
        var i = 0
        while (i < n) {
            val dr = r - (colorTab[i++].toInt() and 0xff)
            val dg = g - (colorTab[i++].toInt() and 0xff)
            val db = b - (colorTab[i++].toInt() and 0xff)
            val d = dr * dr + dg * dg + db * db
            val idx = i / 3 - 1
            if (usedEntry[idx] && d < dmin) { dmin = d; minPos = idx }
        }
        return minPos
    }

    private fun writeLSD() {
        writeShort(width); writeShort(height)
        out?.write(0x80 or 0x70 or 0x00 or palSize)
        out?.write(0); out?.write(0)
    }

    private fun writeNetscapeExt() {
        out?.write(0x21); out?.write(0xFF); out?.write(11)
        out?.write("NETSCAPE2.0".toByteArray())
        out?.write(3); out?.write(1); writeShort(repeat); out?.write(0)
    }

    private fun writeGraphicCtrlExt() {
        out?.write(0x21); out?.write(0xF9); out?.write(4)
        val transp: Int; val disp: Int
        if (transparent == Color.TRANSPARENT) { transp = 0; disp = 0 } else { transp = 1; disp = 2 }
        out?.write(0 or (disp shl 2) or 0 or transp)
        writeShort(delay)
        out?.write(transIndex); out?.write(0)
    }

    private fun writeImageDesc() {
        out?.write(0x2C); writeShort(0); writeShort(0)
        writeShort(width); writeShort(height)
        if (firstFrame) out?.write(0) else out?.write(0x80 or palSize)
    }

    private fun writePalette() {
        out?.write(colorTab)
        val n = (3 * 256) - colorTab.size
        for (i in 0 until n) out?.write(0)
    }

    private fun writePixels() {
        val encoder = LzwEncoder(width, height, indexedPixels, colorDepth)
        encoder.encode(out!!)
    }

    private fun writeShort(v: Int) {
        out?.write(v and 0xFF); out?.write((v shr 8) and 0xFF)
    }
}

/** NeuQuant neural-network colour quantizer. Public domain by Anthony Dekker. */
internal class NeuQuant(private val thepicture: ByteArray, private val lengthcount: Int, private val samplefac: Int) {
    private val netsize = 256
    private val network = Array(netsize) { DoubleArray(4) }
    private val netindex = IntArray(256)
    private val bias = IntArray(netsize)
    private val freq = IntArray(netsize)
    private val radpower = IntArray(initrad)

    companion object {
        private const val maxnetpos = 255
        private const val netbiasshift = 4
        private const val ncycles = 100
        private const val intbiasshift = 16
        private const val intbias = 1 shl intbiasshift
        private const val gammashift = 10
        private const val betashift = 10
        private const val beta = intbias shr betashift
        private const val betagamma = intbias shl (gammashift - betashift)
        private const val initrad = netsize shr 3
        private const val radiusbiasshift = 6
        private const val radiusbias = 1 shl radiusbiasshift
        private const val initradius = initrad * radiusbias
        private const val radiusdec = 30
        private const val alphabiasshift = 10
        private const val initalpha = 1 shl alphabiasshift
        private const val alphadec = 30
        private const val radbiasshift = 8
        private const val radbias = 1 shl radbiasshift
        private const val alpharadbshift = alphabiasshift + radbiasshift
        private const val alpharadbias = 1 shl alpharadbshift
    }

    init {
        for (i in 0 until netsize) {
            val p = network[i]
            p[0] = (i shl (netbiasshift + 8)) / netsize.toDouble()
            p[1] = p[0]; p[2] = p[0]; p[3] = i.toDouble()
            freq[i] = intbias / netsize
            bias[i] = 0
        }
    }

    fun process(): ByteArray {
        learn()
        unbiasnet()
        inxbuild()
        return colorMap()
    }

    fun map(r: Int, g: Int, b: Int): Int {
        var bestd = Int.MAX_VALUE; var best = -1
        var i = netindex[g]; var j = i - 1
        while (i < netsize || j >= 0) {
            if (i < netsize) {
                val p = network[i]
                val dist = Math.abs(p[1].toInt() - g)
                if (dist >= bestd) { i = netsize } else {
                    val d = dist + Math.abs(p[0].toInt() - b) + Math.abs(p[2].toInt() - r)
                    if (d < bestd) { bestd = d; best = p[3].toInt() }
                    i++
                }
            }
            if (j >= 0) {
                val p = network[j]
                val dist = Math.abs(p[1].toInt() - g)
                if (dist >= bestd) { j = -1 } else {
                    val d = dist + Math.abs(p[0].toInt() - b) + Math.abs(p[2].toInt() - r)
                    if (d < bestd) { bestd = d; best = p[3].toInt() }
                    j--
                }
            }
        }
        return best
    }

    private fun learn() {
        val alphadelta = (samplefac * alphadec).coerceAtLeast(1)
        var alpha = initalpha; var radius = initradius
        var rad = radius shr radiusbiasshift
        if (rad <= 1) rad = 0
        for (i in 0 until rad) radpower[i] = alpha * ((rad * rad - i * i) * radbias / (rad * rad))

        val step = if (lengthcount % 499 != 0) 3 * 499 else if (lengthcount % 491 != 0) 3 * 491 else 3 * 487
        var delta = (lengthcount / 3 / ncycles / samplefac).coerceAtLeast(1)
        var pos = 0; var alphadeltaCounter = 0

        for (i in 0 until lengthcount / 3 / samplefac) {
            val b = (thepicture[pos].toInt() and 0xFF) shl netbiasshift
            val g = (thepicture[pos + 1].toInt() and 0xFF) shl netbiasshift
            val r = (thepicture[pos + 2].toInt() and 0xFF) shl netbiasshift
            val j = contest(b, g, r)
            altersingle(alpha, j, b, g, r)
            if (rad != 0) alterneigh(rad, j, b, g, r)
            pos = (pos + step) % lengthcount
            if (++alphadeltaCounter >= delta) {
                alphadeltaCounter = 0; alpha -= alpha / alphadelta; radius -= radius / radiusdec
                rad = radius shr radiusbiasshift
                if (rad <= 1) rad = 0
                for (k in 0 until rad) radpower[k] = alpha * ((rad * rad - k * k) * radbias / (rad * rad))
            }
        }
    }

    private fun altersingle(alpha: Int, i: Int, b: Int, g: Int, r: Int) {
        val p = network[i]
        p[0] -= alpha * (p[0] - b) / initalpha.toDouble()
        p[1] -= alpha * (p[1] - g) / initalpha.toDouble()
        p[2] -= alpha * (p[2] - r) / initalpha.toDouble()
    }

    private fun alterneigh(rad: Int, i: Int, b: Int, g: Int, r: Int) {
        val lo = (i - rad).coerceAtLeast(0); val hi = (i + rad).coerceAtMost(maxnetpos)
        var j = i + 1; var k = i - 1; var m = 1
        while (j <= hi || k >= lo) {
            val a = radpower[m++]
            if (j <= hi) { val p = network[j++]; p[0] -= a * (p[0] - b) / alpharadbias.toDouble(); p[1] -= a * (p[1] - g) / alpharadbias.toDouble(); p[2] -= a * (p[2] - r) / alpharadbias.toDouble() }
            if (k >= lo) { val p = network[k--]; p[0] -= a * (p[0] - b) / alpharadbias.toDouble(); p[1] -= a * (p[1] - g) / alpharadbias.toDouble(); p[2] -= a * (p[2] - r) / alpharadbias.toDouble() }
        }
    }

    private fun contest(b: Int, g: Int, r: Int): Int {
        var bestd = Int.MAX_VALUE; var bestbiasd = bestd; var bestpos = -1; var bestbiaspos = bestpos
        for (i in 0 until netsize) {
            val p = network[i]
            var dist = Math.abs(p[0].toInt() - b) + Math.abs(p[1].toInt() - g) + Math.abs(p[2].toInt() - r)
            if (dist < bestd) { bestd = dist; bestpos = i }
            val biasdist = dist - (bias[i] shr (intbiasshift - netbiasshift))
            if (biasdist < bestbiasd) { bestbiasd = biasdist; bestbiaspos = i }
            val betafreq = freq[i] shr betashift
            freq[i] -= betafreq; bias[i] += betafreq shl gammashift
        }
        freq[bestpos] += beta; bias[bestpos] -= betagamma
        return bestbiaspos
    }

    private fun unbiasnet() { for (i in 0 until netsize) { network[i][0] /= netbiasshift; network[i][1] /= netbiasshift; network[i][2] /= netbiasshift; network[i][3] = i.toDouble() } }

    private fun inxbuild() {
        var previouscol = 0; var startpos = 0
        for (i in 0 until netsize) {
            val p = network[i]; var smallpos = i; var smallval = p[1].toInt()
            for (j in i + 1 until netsize) { val q = network[j]; if (q[1].toInt() < smallval) { smallpos = j; smallval = q[1].toInt() } }
            val q = network[smallpos]
            if (i != smallpos) { val tmp = q.copyOf(); q[0] = p[0]; q[1] = p[1]; q[2] = p[2]; q[3] = p[3]; p[0] = tmp[0]; p[1] = tmp[1]; p[2] = tmp[2]; p[3] = tmp[3] }
            if (smallval != previouscol) { netindex[previouscol] = (startpos + i) shr 1; for (j in previouscol + 1 until smallval) netindex[j] = i; previouscol = smallval; startpos = i }
        }
        netindex[previouscol] = (startpos + maxnetpos) shr 1
        for (j in previouscol + 1..255) netindex[j] = maxnetpos
    }

    private fun colorMap(): ByteArray {
        val map = ByteArray(3 * netsize)
        val index = IntArray(netsize)
        for (i in 0 until netsize) index[network[i][3].toInt()] = i
        var k = 0
        for (i in 0 until netsize) { val j = index[i]; map[k++] = network[j][2].toInt().toByte(); map[k++] = network[j][1].toInt().toByte(); map[k++] = network[j][0].toInt().toByte() }
        return map
    }
}

/** LZW encoder for GIF pixel data. */
internal class LzwEncoder(private val imgW: Int, private val imgH: Int, private val pixAry: ByteArray, initCodeSize: Int) {
    private val EOF = -1
    private val imgX = 0; private val imgY = 0
    private val initCodeSize = initCodeSize.coerceAtLeast(2)
    private var remaining = 0; private var curPixel = 0

    fun encode(os: FileOutputStream) {
        os.write(initCodeSize)
        remaining = imgW * imgH; curPixel = 0
        compress(initCodeSize + 1, os)
        os.write(0)
    }

    private fun compress(initBits: Int, outs: FileOutputStream) {
        val MAXBITS = 12; val MAXMAXCODE = 1 shl MAXBITS
        var nBits = initBits; var maxCode = 1 shl nBits
        val clearCode = 1 shl (initBits - 1); val eofCode = clearCode + 1
        val htab = IntArray(5003); val codetab = IntArray(5003)
        val hsize = 5003; var freeEnt = clearCode + 2
        var clearFlg = false
        var curAccum = 0; var curBits = 0
        val masks = intArrayOf(0x0000,0x0001,0x0003,0x0007,0x000F,0x001F,0x003F,0x007F,0x00FF,0x01FF,0x03FF,0x07FF,0x0FFF)
        val accum = ByteArray(256); var aCount = 0

        fun charOut(c: Int) { accum[aCount++] = c.toByte(); if (aCount >= 254) { outs.write(aCount); outs.write(accum, 0, aCount); aCount = 0 } }
        fun flushChar() { if (aCount > 0) { outs.write(aCount); outs.write(accum, 0, aCount); aCount = 0 } }
        fun output(code: Int) {
            curAccum = curAccum and masks[curBits]; curAccum = if (curBits > 0) curAccum or (code shl curBits) else code; curBits += nBits
            while (curBits >= 8) { charOut(curAccum and 0xFF); curAccum = curAccum shr 8; curBits -= 8 }
            if (freeEnt > maxCode || clearFlg) {
                if (clearFlg) { nBits = initBits; maxCode = 1 shl nBits; clearFlg = false } else { nBits++; maxCode = if (nBits == MAXBITS) MAXMAXCODE else 1 shl nBits }
            }
        }
        fun nextPixel(): Int = if (remaining == 0) EOF else { remaining--; (pixAry[curPixel++].toInt() and 0xFF) }

        htab.fill(-1)
        output(clearCode)
        var ent = nextPixel()
        if (ent != EOF) {
            var hshift = 0; var fcode = hsize; while (fcode < 65536) { hshift++; fcode *= 2 }; hshift = 8 - hshift
            val hmask = hsize - 1
            var c = nextPixel()
            while (c != EOF) {
                fcode = (c shl MAXBITS) + ent
                var i = (c shl hshift) xor ent
                if (htab[i] == fcode) { ent = codetab[i]; c = nextPixel(); continue }
                var disp = if (i == 0) 1 else hsize - i
                if (htab[i] >= 0) { i -= disp; if (i < 0) i += hsize; while (htab[i] >= 0) { if (htab[i] == fcode) { ent = codetab[i]; break }; i -= disp; if (i < 0) i += hsize }; if (htab[i] == fcode) { c = nextPixel(); continue } }
                output(ent); ent = c
                if (freeEnt < MAXMAXCODE) { codetab[i] = freeEnt++; htab[i] = fcode } else { htab.fill(-1); freeEnt = clearCode + 2; clearFlg = true; output(clearCode) }
                c = nextPixel()
            }
            output(ent)
        }
        output(eofCode)
        flushChar()
    }
}
