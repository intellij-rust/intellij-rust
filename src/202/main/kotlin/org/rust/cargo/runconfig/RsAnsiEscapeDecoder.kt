/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import org.rust.stdext.nextOrNull
import java.awt.Color
import kotlin.math.roundToInt

/**
 * Currently IntelliJ Platform supports only 16 ANSI colors (standard colors and high intensity colors). The base
 * [AnsiEscapeDecoder] class simply ignores 8-bit and 24-bit ANSI color escapes. This class converts (quantizes) such
 * escapes to supported 3/4-bit ANSI color escapes. Note that the user can configure color mapping in editor settings
 * (Preferences > Editor > Console Scheme > Console Colors > ANSI Colors). In addition, the themes also set the colors.
 * So, this solution gives us interoperability with existing themes.
 */
class RsAnsiEscapeDecoder : AnsiEscapeDecoder() {
    override fun escapeText(text: String, outputType: Key<*>, textAcceptor: ColoredTextAcceptor) {
        super.escapeText(quantizeAnsiColors(text), outputType, textAcceptor)
    }

    companion object {
        const val CSI: String = "\u001B[" // "Control Sequence Initiator"
        private val ANSI_CONTROL_SEQUENCE_REGEX: Regex = """${StringUtil.escapeToRegexp(CSI)}([^m]*;[^m]*)m""".toRegex()

        private const val ANSI_SET_FOREGROUND_ATTR: Int = 38
        private const val ANSI_SET_BACKGROUND_ATTR: Int = 48

        const val ANSI_24_BIT_COLOR_FORMAT: Int = 2
        const val ANSI_8_BIT_COLOR_FORMAT: Int = 5

        /**
         * Parses ANSI-value codes from text and replaces 8-bit and 24-bit colors with nearest (in Euclidean space)
         * 4-bit value.
         *
         * @param text a string with ANSI escape sequences
         */
        private fun quantizeAnsiColors(text: String): String = text
            .replace(ANSI_CONTROL_SEQUENCE_REGEX) {
                val rawAttributes = it.destructured.component1().split(";").iterator()
                val result = mutableListOf<Int>()
                while (rawAttributes.hasNext()) {
                    val attribute = rawAttributes.parseAttribute() ?: continue
                    if (attribute !in listOf(ANSI_SET_FOREGROUND_ATTR, ANSI_SET_BACKGROUND_ATTR)) {
                        result.add(attribute)
                        continue
                    }
                    val color = parseColor(rawAttributes) ?: continue
                    val ansiColor = getNearestAnsiColor(color) ?: continue
                    val colorAttribute = getColorAttribute(ansiColor, attribute == ANSI_SET_FOREGROUND_ATTR)
                    result.add(colorAttribute)
                }
                result.joinToString(separator = ";", prefix = CSI, postfix = "m") { attr -> attr.toString() }
            }

        private fun Iterator<String>.parseAttribute(): Int? = nextOrNull()?.toIntOrNull()

        private fun parseColor(rawAttributes: Iterator<String>): Color? {
            val format = rawAttributes.parseAttribute() ?: return null
            return when (format) {
                ANSI_24_BIT_COLOR_FORMAT -> parse24BitColor(rawAttributes)
                ANSI_8_BIT_COLOR_FORMAT -> parse8BitColor(rawAttributes)
                else -> null
            }
        }

        private fun parse24BitColor(rawAttributes: Iterator<String>): Color? {
            val red = rawAttributes.parseAttribute() ?: return null
            val green = rawAttributes.parseAttribute() ?: return null
            val blue = rawAttributes.parseAttribute() ?: return null
            return Color(red, green, blue)
        }

        private fun parse8BitColor(rawAttributes: Iterator<String>): Color? {
            val attribute = rawAttributes.parseAttribute() ?: return null
            return when (attribute) {
                // Standard colors or high intensity colors
                in 0..15 -> Ansi4BitColor[attribute]?.value

                // 6 × 6 × 6 cube (216 colors): 16 + 36 × r + 6 × g + b (0 ≤ r, g, b ≤ 5)
                in 16..231 -> {
                    val red = (attribute - 16) / 36 * 51
                    val green = (attribute - 16) % 36 / 6 * 51
                    val blue = (attribute - 16) % 6 * 51
                    Color(red, green, blue)
                }

                // Grayscale from black to white in 24 steps
                in 232..255 -> {
                    val value = (attribute - 232) * 10 + 8
                    Color(value, value, value)
                }

                else -> null
            }
        }

        private fun getNearestAnsiColor(color: Color): Ansi4BitColor? =
            Ansi4BitColor.values().minBy { calcEuclideanDistance(it.value, color) }

        private fun calcEuclideanDistance(from: Color, to: Color): Int {
            val redDiff = from.red.toDouble() - to.red
            val greenDiff = from.green.toDouble() - to.green
            val blueDiff = from.blue.toDouble() - to.blue
            return Math.sqrt(redDiff * redDiff + greenDiff * greenDiff + blueDiff * blueDiff).roundToInt()
        }

        private fun getColorAttribute(realAnsiColor: Ansi4BitColor, isForeground: Boolean): Int {
            // Rude hack for Windows: map the bright white foreground color to black.
            // See https://github.com/intellij-rust/intellij-rust/pull/3312#issue-249111003
            val ansiColor = if (realAnsiColor == Ansi4BitColor.BRIGHT_WHITE && isForeground && SystemInfo.isWindows) {
                Ansi4BitColor.BLACK
            } else {
                realAnsiColor
            }
            val colorIndex = ansiColor.index
            return when {
                colorIndex in 0..7 && isForeground -> colorIndex + 30
                colorIndex in 0..7 && !isForeground -> colorIndex + 40
                colorIndex in 8..15 && isForeground -> colorIndex + 82
                colorIndex in 8..15 && !isForeground -> colorIndex + 92
                else -> error("impossible")
            }
        }

        private enum class Ansi4BitColor(val value: Color) {
            BLACK(Color(0, 0, 0)),
            RED(Color(128, 0, 0)),
            GREEN(Color(0, 128, 0)),
            YELLOW(Color(128, 128, 0)),
            BLUE(Color(0, 0, 128)),
            MAGENTA(Color(128, 0, 128)),
            CYAN(Color(0, 128, 128)),
            WHITE(Color(192, 192, 192)),
            BRIGHT_BLACK(Color(128, 128, 128)),
            BRIGHT_RED(Color(255, 0, 0)),
            BRIGHT_GREEN(Color(0, 255, 0)),
            BRIGHT_YELLOW(Color(255, 255, 0)),
            BRIGHT_BLUE(Color(0, 0, 255)),
            BRIGHT_MAGENTA(Color(255, 0, 255)),
            BRIGHT_CYAN(Color(0, 255, 255)),
            BRIGHT_WHITE(Color(255, 255, 255));

            val index: Int get() = values().indexOf(this)

            companion object {
                operator fun get(index: Int): Ansi4BitColor? {
                    val values = values()
                    return if (index >= 0 && index < values.size) values[index] else null
                }
            }
        }
    }
}
