package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin, zero-dependency ISO/IEC 18004 QR Code Byte-Mode Encoder (Versions 1..10, ECC Level L).
 *
 * Supports URLs up to 271 UTF-8 bytes without adding ZXing or any external library to the APK,
 * keeping Feather Browser's ~1.6 MB footprint intact.
 */
object QrCodeGenerator {

    private data class VersionSpec(
        val version: Int,
        val size: Int,
        val totalDataCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val numBlocksGroup1: Int,
        val dataCodewordsGroup1: Int,
        val numBlocksGroup2: Int,
        val dataCodewordsGroup2: Int,
        val alignmentCenters: IntArray
    )

    // Standard QR specifications for ECC Level L (7% error recovery — maximum URL capacity & crisp module density)
    private val VERSION_SPECS = arrayOf(
        VersionSpec(1, 21, 19, 7, 1, 19, 0, 0, intArrayOf()),
        VersionSpec(2, 25, 34, 10, 1, 34, 0, 0, intArrayOf(6, 18)),
        VersionSpec(3, 29, 55, 15, 1, 55, 0, 0, intArrayOf(6, 22)),
        VersionSpec(4, 33, 80, 20, 1, 80, 0, 0, intArrayOf(6, 26)),
        VersionSpec(5, 37, 108, 26, 1, 108, 0, 0, intArrayOf(6, 30)),
        VersionSpec(6, 41, 136, 18, 2, 68, 0, 0, intArrayOf(6, 34)),
        VersionSpec(7, 45, 156, 20, 2, 78, 0, 0, intArrayOf(6, 22, 38)),
        VersionSpec(8, 49, 194, 24, 2, 97, 0, 0, intArrayOf(6, 24, 42)),
        VersionSpec(9, 53, 232, 30, 2, 116, 0, 0, intArrayOf(6, 26, 46)),
        VersionSpec(10, 57, 274, 18, 2, 68, 2, 69, intArrayOf(6, 28, 50))
    )

    // Version information bitstrings for versions 7..10
    private val VERSION_INFO_BITS = mapOf(
        7 to 0x07C94,
        8 to 0x085BC,
        9 to 0x09A99,
        10 to 0x0A4D3
    )

    /**
     * Encodes [text] into a boolean matrix (`true` = dark module, `false` = light module).
     * Returns `null` if [text] is blank or exceeds Version 10 capacity (271 UTF-8 bytes).
     */
    fun encodeMatrix(text: String): Array<BooleanArray>? {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        if (bytes.isEmpty()) return null

        val spec = selectVersion(bytes.size) ?: return null
        val dataCodewords = buildDataCodewords(bytes, spec)
        val finalMessage = interleaveWithEcc(dataCodewords, spec)

        val size = spec.size
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        drawFunctionPatterns(modules, isFunction, spec)
        drawCodewords(modules, isFunction, finalMessage, size)

        // Choose best mask (0..7) with lowest penalty score
        var bestMask = 0
        var minPenalty = Int.MAX_VALUE
        var bestGrid: Array<BooleanArray>? = null

        for (mask in 0..7) {
            val candidate = Array(size) { r -> modules[r].clone() }
            applyMask(candidate, isFunction, mask, size)
            drawFormatBits(candidate, isFunction, mask, size, markFunction = false)
            val penalty = computePenalty(candidate, size)
            if (penalty < minPenalty) {
                minPenalty = penalty
                bestMask = mask
                bestGrid = candidate
            }
        }

        return bestGrid ?: run {
            applyMask(modules, isFunction, bestMask, size)
            drawFormatBits(modules, isFunction, bestMask, size, markFunction = false)
            modules
        }
    }

    /**
     * Generates a sharp, crisp [Bitmap] with a 4-module quiet zone around the QR code.
     */
    fun generateBitmap(
        text: String,
        targetSizePx: Int = 512,
        darkColor: Int = AndroidColor.BLACK,
        lightColor: Int = AndroidColor.WHITE
    ): Bitmap? {
        val matrix = encodeMatrix(text) ?: return null
        val matrixSize = matrix.size
        val quietZone = 4
        val totalModules = matrixSize + quietZone * 2
        val scale = (targetSizePx / totalModules).coerceAtLeast(4)
        val bitmapSize = totalModules * scale

        val pixels = IntArray(bitmapSize * bitmapSize) { lightColor }
        for (y in 0 until matrixSize) {
            for (x in 0 until matrixSize) {
                if (matrix[y][x]) {
                    val startY = (y + quietZone) * scale
                    val startX = (x + quietZone) * scale
                    for (dy in 0 until scale) {
                        val rowOffset = (startY + dy) * bitmapSize + startX
                        for (dx in 0 until scale) {
                            pixels[rowOffset + dx] = darkColor
                        }
                    }
                }
            }
        }

        return Bitmap.createBitmap(pixels, bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
    }

    private fun selectVersion(byteLength: Int): VersionSpec? {
        for (spec in VERSION_SPECS) {
            val charCountBits = if (spec.version <= 9) 8 else 16
            val capacityBits = spec.totalDataCodewords * 8
            val requiredBits = 4 + charCountBits + byteLength * 8
            if (requiredBits <= capacityBits) {
                return spec
            }
        }
        return null
    }

    private fun buildDataCodewords(bytes: ByteArray, spec: VersionSpec): IntArray {
        val capacityBits = spec.totalDataCodewords * 8
        val bits = ArrayList<Int>(capacityBits + 16)

        fun appendBits(value: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Byte mode indicator = 0100
        appendBits(0b0100, 4)
        // Character count indicator (8 bits for v1..9, 16 bits for v10+)
        val countBits = if (spec.version <= 9) 8 else 16
        appendBits(bytes.size, countBits)

        for (b in bytes) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        // Terminator: up to 4 zero bits
        val terminatorLen = minOf(4, capacityBits - bits.size)
        appendBits(0, terminatorLen)

        // Pad to byte boundary
        val rem = bits.size % 8
        if (rem != 0) {
            appendBits(0, 8 - rem)
        }

        // Pad codewords alternating 0xEC and 0x11
        var padIndex = 0
        while (bits.size < capacityBits) {
            appendBits(if (padIndex % 2 == 0) 0xEC else 0x11, 8)
            padIndex++
        }

        val codewords = IntArray(spec.totalDataCodewords)
        for (i in codewords.indices) {
            var b = 0
            for (j in 0..7) {
                b = (b shl 1) or bits[i * 8 + j]
            }
            codewords[i] = b
        }
        return codewords
    }

    private fun interleaveWithEcc(dataCodewords: IntArray, spec: VersionSpec): IntArray {
        val dataBlocks = ArrayList<IntArray>()
        val eccBlocks = ArrayList<IntArray>()
        val generator = reedSolomonGenerator(spec.ecCodewordsPerBlock)

        var offset = 0
        repeat(spec.numBlocksGroup1) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.dataCodewordsGroup1)
            offset += spec.dataCodewordsGroup1
            dataBlocks.add(block)
            eccBlocks.add(reedSolomonRemainder(block, generator))
        }
        repeat(spec.numBlocksGroup2) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.dataCodewordsGroup2)
            offset += spec.dataCodewordsGroup2
            dataBlocks.add(block)
            eccBlocks.add(reedSolomonRemainder(block, generator))
        }

        val totalBlocks = dataBlocks.size
        val totalCodewords = spec.totalDataCodewords + totalBlocks * spec.ecCodewordsPerBlock
        val result = IntArray(totalCodewords)
        var pos = 0

        val maxDataLen = maxOf(spec.dataCodewordsGroup1, spec.dataCodewordsGroup2)
        for (i in 0 until maxDataLen) {
            for (b in 0 until totalBlocks) {
                if (i < dataBlocks[b].size) {
                    result[pos++] = dataBlocks[b][i]
                }
            }
        }
        for (i in 0 until spec.ecCodewordsPerBlock) {
            for (b in 0 until totalBlocks) {
                result[pos++] = eccBlocks[b][i]
            }
        }
        return result
    }

    // GF(256) with primitive polynomial 0x11D
    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun reedSolomonGenerator(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        repeat(degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < degree) {
                    result[j] = result[j] xor result[j + 1]
                }
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonRemainder(data: IntArray, generator: IntArray): IntArray {
        val result = IntArray(generator.size)
        for (b in data) {
            val factor = b xor result[0]
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in generator.indices) {
                result[i] = result[i] xor gfMultiply(generator[i], factor)
            }
        }
        return result
    }

    private fun setFunctionModule(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        x: Int,
        y: Int,
        isDark: Boolean
    ) {
        modules[y][x] = isDark
        isFunction[y][x] = true
    }

    private fun drawFunctionPatterns(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        spec: VersionSpec
    ) {
        val size = spec.size

        // Timing patterns
        for (i in 0 until size) {
            setFunctionModule(modules, isFunction, 6, i, i % 2 == 0)
            setFunctionModule(modules, isFunction, i, 6, i % 2 == 0)
        }

        // 3 Finder patterns (top-left, top-right, bottom-left)
        drawFinderPattern(modules, isFunction, 3, 3, size)
        drawFinderPattern(modules, isFunction, size - 4, 3, size)
        drawFinderPattern(modules, isFunction, 3, size - 4, size)

        // Alignment patterns
        val centers = spec.alignmentCenters
        val numAlign = centers.size
        for (i in 0 until numAlign) {
            for (j in 0 until numAlign) {
                // Skip corners occupied by finder patterns
                if ((i == 0 && j == 0) ||
                    (i == 0 && j == numAlign - 1) ||
                    (i == numAlign - 1 && j == 0)
                ) continue
                drawAlignmentPattern(modules, isFunction, centers[i], centers[j])
            }
        }

        // Reserve format bits + dark module
        drawFormatBits(modules, isFunction, mask = 0, size = size, markFunction = true)

        // Draw version blocks if version >= 7
        VERSION_INFO_BITS[spec.version]?.let { bits ->
            for (i in 0 until 18) {
                val bit = ((bits ushr i) and 1) != 0
                val a = size - 11 + i % 3
                val b = i / 3
                setFunctionModule(modules, isFunction, a, b, bit)
                setFunctionModule(modules, isFunction, b, a, bit)
            }
        }
    }

    private fun drawFinderPattern(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        cx: Int,
        cy: Int,
        size: Int
    ) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) {
                    val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    val dark = dist != 2 && dist != 4
                    setFunctionModule(modules, isFunction, x, y, dark)
                }
            }
        }
    }

    private fun drawAlignmentPattern(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        cx: Int,
        cy: Int
    ) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                setFunctionModule(modules, isFunction, cx + dx, cy + dy, dist != 1)
            }
        }
    }

    private fun drawFormatBits(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        mask: Int,
        size: Int,
        markFunction: Boolean
    ) {
        // ECC Level L format bits = 01
        val data = (0b01 shl 3) or mask
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        fun put(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            if (markFunction) isFunction[y][x] = true
        }

        // First copy around top-left finder
        for (i in 0..5) put(8, i, ((bits ushr i) and 1) != 0)
        put(8, 7, ((bits ushr 6) and 1) != 0)
        put(8, 8, ((bits ushr 7) and 1) != 0)
        put(7, 8, ((bits ushr 8) and 1) != 0)
        for (i in 9..14) put(14 - i, 8, ((bits ushr i) and 1) != 0)

        // Second copy along top-right and bottom-left
        for (i in 0..7) put(size - 1 - i, 8, ((bits ushr i) and 1) != 0)
        for (i in 8..14) put(8, size - 15 + i, ((bits ushr i) and 1) != 0)

        // Fixed dark module
        put(8, size - 8, true)
    }

    private fun drawCodewords(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        codewords: IntArray,
        size: Int
    ) {
        var bitIndex = 0
        val totalBits = codewords.size * 8

        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!isFunction[y][x] && bitIndex < totalBits) {
                        val dark = ((codewords[bitIndex ushr 3] ushr (7 - (bitIndex and 7))) and 1) != 0
                        modules[y][x] = dark
                        bitIndex++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        mask: Int,
        size: Int
    ) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isFunction[y][x]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (y / 2 + x / 3) % 2 == 0
                    5 -> (x * y) % 2 + (x * y) % 3 == 0
                    6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
                    7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
                    else -> false
                }
                if (invert) modules[y][x] = !modules[y][x]
            }
        }
    }

    private fun computePenalty(modules: Array<BooleanArray>, size: Int): Int {
        var penalty = 0

        // Adjacent modules in row/column having same color
        for (y in 0 until size) {
            var runColor = false
            var runX = 0
            for (x in 0 until size) {
                if (x == 0 || modules[y][x] != runColor) {
                    runColor = modules[y][x]
                    runX = 1
                } else {
                    runX++
                    if (runX == 5) penalty += 3
                    else if (runX > 5) penalty += 1
                }
            }
        }
        for (x in 0 until size) {
            var runColor = false
            var runY = 0
            for (y in 0 until size) {
                if (y == 0 || modules[y][x] != runColor) {
                    runColor = modules[y][x]
                    runY = 1
                } else {
                    runY++
                    if (runY == 5) penalty += 3
                    else if (runY > 5) penalty += 1
                }
            }
        }

        // 2x2 blocks of same color
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    penalty += 3
                }
            }
        }

        // Dark module balance
        var darkCount = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (modules[y][x]) darkCount++
            }
        }
        val total = size * size
        val k = (kotlin.math.abs(darkCount * 20 - total * 10) + total - 1) / total - 1
        if (k > 0) penalty += k * 10

        return penalty
    }
}
