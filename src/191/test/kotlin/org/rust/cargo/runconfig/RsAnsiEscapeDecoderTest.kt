/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestCase
import org.rust.cargo.runconfig.RsAnsiEscapeDecoder.Companion.ANSI_24_BIT_COLOR_FORMAT
import org.rust.cargo.runconfig.RsAnsiEscapeDecoder.Companion.ANSI_8_BIT_COLOR_FORMAT
import org.rust.cargo.runconfig.RsAnsiEscapeDecoder.Companion.CSI

class RsAnsiEscapeDecoderTest : PlatformTestCase() {

    fun `test standard 24-bit colors foreground`() = check(
        ColoredText(
            make24BitColorCtrlSeq(0, 0, 0, isForeground = true) + "BLACK" +
                make24BitColorCtrlSeq(129, 0, 0, isForeground = true) + "RED" +
                make24BitColorCtrlSeq(0, 129, 0, isForeground = true) + "GREEN" +
                make24BitColorCtrlSeq(129, 129, 0, isForeground = true) + "YELLOW" +
                make24BitColorCtrlSeq(0, 0, 129, isForeground = true) + "BLUE" +
                make24BitColorCtrlSeq(129, 0, 129, isForeground = true) + "MAGENTA" +
                make24BitColorCtrlSeq(0, 129, 129, isForeground = true) + "CYAN" +
                make24BitColorCtrlSeq(193, 193, 193, isForeground = true) + "WHITE"
        )
            .addExpected("BLACK", makeSimpleCtrlSeq(30))
            .addExpected("RED", makeSimpleCtrlSeq(31))
            .addExpected("GREEN", makeSimpleCtrlSeq(32))
            .addExpected("YELLOW", makeSimpleCtrlSeq(33))
            .addExpected("BLUE", makeSimpleCtrlSeq(34))
            .addExpected("MAGENTA", makeSimpleCtrlSeq(35))
            .addExpected("CYAN", makeSimpleCtrlSeq(36))
            .addExpected("WHITE", makeSimpleCtrlSeq(37))
    )

    fun `test standard 24-bit colors background`() = check(
        ColoredText(
            make24BitColorCtrlSeq(0, 0, 0, isForeground = false) + "BLACK" +
                make24BitColorCtrlSeq(129, 0, 0, isForeground = false) + "RED" +
                make24BitColorCtrlSeq(0, 129, 0, isForeground = false) + "GREEN" +
                make24BitColorCtrlSeq(129, 129, 0, isForeground = false) + "YELLOW" +
                make24BitColorCtrlSeq(0, 0, 129, isForeground = false) + "BLUE" +
                make24BitColorCtrlSeq(129, 0, 129, isForeground = false) + "MAGENTA" +
                make24BitColorCtrlSeq(0, 129, 129, isForeground = false) + "CYAN" +
                make24BitColorCtrlSeq(193, 193, 193, isForeground = false) + "WHITE"
        )
            .addExpected("BLACK", makeSimpleCtrlSeq(40))
            .addExpected("RED", makeSimpleCtrlSeq(41))
            .addExpected("GREEN", makeSimpleCtrlSeq(42))
            .addExpected("YELLOW", makeSimpleCtrlSeq(43))
            .addExpected("BLUE", makeSimpleCtrlSeq(44))
            .addExpected("MAGENTA", makeSimpleCtrlSeq(45))
            .addExpected("CYAN", makeSimpleCtrlSeq(46))
            .addExpected("WHITE", makeSimpleCtrlSeq(47))
    )

    fun `test bright 24-bit colors foreground`() = check(
        ColoredText(
            make24BitColorCtrlSeq(127, 127, 127, isForeground = true) + "BRIGHT BLACK" +
                make24BitColorCtrlSeq(254, 0, 0, isForeground = true) + "BRIGHT RED" +
                make24BitColorCtrlSeq(0, 254, 0, isForeground = true) + "BRIGHT GREEN" +
                make24BitColorCtrlSeq(254, 254, 0, isForeground = true) + "BRIGHT YELLOW" +
                make24BitColorCtrlSeq(0, 0, 254, isForeground = true) + "BRIGHT BLUE" +
                make24BitColorCtrlSeq(254, 0, 254, isForeground = true) + "BRIGHT MAGENTA" +
                make24BitColorCtrlSeq(0, 254, 254, isForeground = true) + "BRIGHT CYAN" +
                make24BitColorCtrlSeq(254, 254, 254, isForeground = true) + "BRIGHT WHITE"
        )
            .addExpected("BRIGHT BLACK", makeSimpleCtrlSeq(90))
            .addExpected("BRIGHT RED", makeSimpleCtrlSeq(91))
            .addExpected("BRIGHT GREEN", makeSimpleCtrlSeq(92))
            .addExpected("BRIGHT YELLOW", makeSimpleCtrlSeq(93))
            .addExpected("BRIGHT BLUE", makeSimpleCtrlSeq(94))
            .addExpected("BRIGHT MAGENTA", makeSimpleCtrlSeq(95))
            .addExpected("BRIGHT CYAN", makeSimpleCtrlSeq(96))
            .addExpected("BRIGHT WHITE", if (SystemInfo.isWindows) makeSimpleCtrlSeq(30) else makeSimpleCtrlSeq(97))
    )

    fun `test bright 24-bit colors background`() = check(
        ColoredText(
            make24BitColorCtrlSeq(127, 127, 127, isForeground = false) + "BRIGHT BLACK" +
                make24BitColorCtrlSeq(254, 0, 0, isForeground = false) + "BRIGHT RED" +
                make24BitColorCtrlSeq(0, 254, 0, isForeground = false) + "BRIGHT GREEN" +
                make24BitColorCtrlSeq(254, 254, 0, isForeground = false) + "BRIGHT YELLOW" +
                make24BitColorCtrlSeq(0, 0, 254, isForeground = false) + "BRIGHT BLUE" +
                make24BitColorCtrlSeq(254, 0, 254, isForeground = false) + "BRIGHT MAGENTA" +
                make24BitColorCtrlSeq(0, 254, 254, isForeground = false) + "BRIGHT CYAN" +
                make24BitColorCtrlSeq(254, 254, 254, isForeground = false) + "BRIGHT WHITE"
        )
            .addExpected("BRIGHT BLACK", makeSimpleCtrlSeq(100))
            .addExpected("BRIGHT RED", makeSimpleCtrlSeq(101))
            .addExpected("BRIGHT GREEN", makeSimpleCtrlSeq(102))
            .addExpected("BRIGHT YELLOW", makeSimpleCtrlSeq(103))
            .addExpected("BRIGHT BLUE", makeSimpleCtrlSeq(104))
            .addExpected("BRIGHT MAGENTA", makeSimpleCtrlSeq(105))
            .addExpected("BRIGHT CYAN", makeSimpleCtrlSeq(106))
            .addExpected("BRIGHT WHITE", makeSimpleCtrlSeq(107))
    )

    fun `test standard 8-bit colors foreground`() = check(
        ColoredText(
            make8BitColorCtrlSeq(0, isForeground = true) + "BLACK" +
                make8BitColorCtrlSeq(1, isForeground = true) + "RED" +
                make8BitColorCtrlSeq(2, isForeground = true) + "GREEN" +
                make8BitColorCtrlSeq(3, isForeground = true) + "YELLOW" +
                make8BitColorCtrlSeq(4, isForeground = true) + "BLUE" +
                make8BitColorCtrlSeq(5, isForeground = true) + "MAGENTA" +
                make8BitColorCtrlSeq(6, isForeground = true) + "CYAN" +
                make8BitColorCtrlSeq(7, isForeground = true) + "WHITE"
        )
            .addExpected("BLACK", makeSimpleCtrlSeq(30))
            .addExpected("RED", makeSimpleCtrlSeq(31))
            .addExpected("GREEN", makeSimpleCtrlSeq(32))
            .addExpected("YELLOW", makeSimpleCtrlSeq(33))
            .addExpected("BLUE", makeSimpleCtrlSeq(34))
            .addExpected("MAGENTA", makeSimpleCtrlSeq(35))
            .addExpected("CYAN", makeSimpleCtrlSeq(36))
            .addExpected("WHITE", makeSimpleCtrlSeq(37))
    )

    fun `test standard 8-bit colors background`() = check(
        ColoredText(
            make8BitColorCtrlSeq(0, isForeground = false) + "BLACK" +
                make8BitColorCtrlSeq(1, isForeground = false) + "RED" +
                make8BitColorCtrlSeq(2, isForeground = false) + "GREEN" +
                make8BitColorCtrlSeq(3, isForeground = false) + "YELLOW" +
                make8BitColorCtrlSeq(4, isForeground = false) + "BLUE" +
                make8BitColorCtrlSeq(5, isForeground = false) + "MAGENTA" +
                make8BitColorCtrlSeq(6, isForeground = false) + "CYAN" +
                make8BitColorCtrlSeq(7, isForeground = false) + "WHITE"
        )
            .addExpected("BLACK", makeSimpleCtrlSeq(40))
            .addExpected("RED", makeSimpleCtrlSeq(41))
            .addExpected("GREEN", makeSimpleCtrlSeq(42))
            .addExpected("YELLOW", makeSimpleCtrlSeq(43))
            .addExpected("BLUE", makeSimpleCtrlSeq(44))
            .addExpected("MAGENTA", makeSimpleCtrlSeq(45))
            .addExpected("CYAN", makeSimpleCtrlSeq(46))
            .addExpected("WHITE", makeSimpleCtrlSeq(47))
    )

    fun `test bright 8-bit colors foreground`() = check(
        ColoredText(
            make8BitColorCtrlSeq(8, isForeground = true) + "BRIGHT BLACK" +
                make8BitColorCtrlSeq(9, isForeground = true) + "BRIGHT RED" +
                make8BitColorCtrlSeq(10, isForeground = true) + "BRIGHT GREEN" +
                make8BitColorCtrlSeq(11, isForeground = true) + "BRIGHT YELLOW" +
                make8BitColorCtrlSeq(12, isForeground = true) + "BRIGHT BLUE" +
                make8BitColorCtrlSeq(13, isForeground = true) + "BRIGHT MAGENTA" +
                make8BitColorCtrlSeq(14, isForeground = true) + "BRIGHT CYAN" +
                make8BitColorCtrlSeq(15, isForeground = true) + "BRIGHT WHITE"
        )
            .addExpected("BRIGHT BLACK", makeSimpleCtrlSeq(90))
            .addExpected("BRIGHT RED", makeSimpleCtrlSeq(91))
            .addExpected("BRIGHT GREEN", makeSimpleCtrlSeq(92))
            .addExpected("BRIGHT YELLOW", makeSimpleCtrlSeq(93))
            .addExpected("BRIGHT BLUE", makeSimpleCtrlSeq(94))
            .addExpected("BRIGHT MAGENTA", makeSimpleCtrlSeq(95))
            .addExpected("BRIGHT CYAN", makeSimpleCtrlSeq(96))
            .addExpected("BRIGHT WHITE", if (SystemInfo.isWindows) makeSimpleCtrlSeq(30) else makeSimpleCtrlSeq(97))
    )

    fun `test bright 8-bit colors background`() = check(
        ColoredText(
            make8BitColorCtrlSeq(8, isForeground = false) + "BRIGHT BLACK" +
                make8BitColorCtrlSeq(9, isForeground = false) + "BRIGHT RED" +
                make8BitColorCtrlSeq(10, isForeground = false) + "BRIGHT GREEN" +
                make8BitColorCtrlSeq(11, isForeground = false) + "BRIGHT YELLOW" +
                make8BitColorCtrlSeq(12, isForeground = false) + "BRIGHT BLUE" +
                make8BitColorCtrlSeq(13, isForeground = false) + "BRIGHT MAGENTA" +
                make8BitColorCtrlSeq(14, isForeground = false) + "BRIGHT CYAN" +
                make8BitColorCtrlSeq(15, isForeground = false) + "BRIGHT WHITE"
        )
            .addExpected("BRIGHT BLACK", makeSimpleCtrlSeq(100))
            .addExpected("BRIGHT RED", makeSimpleCtrlSeq(101))
            .addExpected("BRIGHT GREEN", makeSimpleCtrlSeq(102))
            .addExpected("BRIGHT YELLOW", makeSimpleCtrlSeq(103))
            .addExpected("BRIGHT BLUE", makeSimpleCtrlSeq(104))
            .addExpected("BRIGHT MAGENTA", makeSimpleCtrlSeq(105))
            .addExpected("BRIGHT CYAN", makeSimpleCtrlSeq(106))
            .addExpected("BRIGHT WHITE", makeSimpleCtrlSeq(107))
    )

    fun `test rgb 8-bit colors foreground`() = check(
        ColoredText(
            make8BitColorCtrlSeq(232, isForeground = true) + "BLACK" +
                make8BitColorCtrlSeq(88, isForeground = true) + "RED" +
                make8BitColorCtrlSeq(28, isForeground = true) + "GREEN" +
                make8BitColorCtrlSeq(142, isForeground = true) + "YELLOW" +
                make8BitColorCtrlSeq(18, isForeground = true) + "BLUE" +
                make8BitColorCtrlSeq(90, isForeground = true) + "MAGENTA" +
                make8BitColorCtrlSeq(30, isForeground = true) + "CYAN" +
                make8BitColorCtrlSeq(250, isForeground = true) + "WHITE" +
                make8BitColorCtrlSeq(240, isForeground = true) + "BRIGHT BLACK" +
                make8BitColorCtrlSeq(196, isForeground = true) + "BRIGHT RED" +
                make8BitColorCtrlSeq(46, isForeground = true) + "BRIGHT GREEN" +
                make8BitColorCtrlSeq(226, isForeground = true) + "BRIGHT YELLOW" +
                make8BitColorCtrlSeq(21, isForeground = true) + "BRIGHT BLUE" +
                make8BitColorCtrlSeq(201, isForeground = true) + "BRIGHT MAGENTA" +
                make8BitColorCtrlSeq(51, isForeground = true) + "BRIGHT CYAN" +
                make8BitColorCtrlSeq(255, isForeground = true) + "BRIGHT WHITE"
        )
            .addExpected("BLACK", makeSimpleCtrlSeq(30))
            .addExpected("RED", makeSimpleCtrlSeq(31))
            .addExpected("GREEN", makeSimpleCtrlSeq(32))
            .addExpected("YELLOW", makeSimpleCtrlSeq(33))
            .addExpected("BLUE", makeSimpleCtrlSeq(34))
            .addExpected("MAGENTA", makeSimpleCtrlSeq(35))
            .addExpected("CYAN", makeSimpleCtrlSeq(36))
            .addExpected("WHITE", makeSimpleCtrlSeq(37))
            .addExpected("BRIGHT BLACK", makeSimpleCtrlSeq(90))
            .addExpected("BRIGHT RED", makeSimpleCtrlSeq(91))
            .addExpected("BRIGHT GREEN", makeSimpleCtrlSeq(92))
            .addExpected("BRIGHT YELLOW", makeSimpleCtrlSeq(93))
            .addExpected("BRIGHT BLUE", makeSimpleCtrlSeq(94))
            .addExpected("BRIGHT MAGENTA", makeSimpleCtrlSeq(95))
            .addExpected("BRIGHT CYAN", makeSimpleCtrlSeq(96))
            .addExpected("BRIGHT WHITE", if (SystemInfo.isWindows) makeSimpleCtrlSeq(30) else makeSimpleCtrlSeq(97))
    )

    fun `test rgb 8-bit colors background`() = check(
        ColoredText(
            make8BitColorCtrlSeq(232, isForeground = false) + "BLACK" +
                make8BitColorCtrlSeq(88, isForeground = false) + "RED" +
                make8BitColorCtrlSeq(28, isForeground = false) + "GREEN" +
                make8BitColorCtrlSeq(142, isForeground = false) + "YELLOW" +
                make8BitColorCtrlSeq(18, isForeground = false) + "BLUE" +
                make8BitColorCtrlSeq(90, isForeground = false) + "MAGENTA" +
                make8BitColorCtrlSeq(30, isForeground = false) + "CYAN" +
                make8BitColorCtrlSeq(250, isForeground = false) + "WHITE" +
                make8BitColorCtrlSeq(240, isForeground = false) + "BRIGHT BLACK" +
                make8BitColorCtrlSeq(196, isForeground = false) + "BRIGHT RED" +
                make8BitColorCtrlSeq(46, isForeground = false) + "BRIGHT GREEN" +
                make8BitColorCtrlSeq(226, isForeground = false) + "BRIGHT YELLOW" +
                make8BitColorCtrlSeq(21, isForeground = false) + "BRIGHT BLUE" +
                make8BitColorCtrlSeq(201, isForeground = false) + "BRIGHT MAGENTA" +
                make8BitColorCtrlSeq(51, isForeground = false) + "BRIGHT CYAN" +
                make8BitColorCtrlSeq(255, isForeground = false) + "BRIGHT WHITE"
        )
            .addExpected("BLACK", makeSimpleCtrlSeq(40))
            .addExpected("RED", makeSimpleCtrlSeq(41))
            .addExpected("GREEN", makeSimpleCtrlSeq(42))
            .addExpected("YELLOW", makeSimpleCtrlSeq(43))
            .addExpected("BLUE", makeSimpleCtrlSeq(44))
            .addExpected("MAGENTA", makeSimpleCtrlSeq(45))
            .addExpected("CYAN", makeSimpleCtrlSeq(46))
            .addExpected("WHITE", makeSimpleCtrlSeq(47))
            .addExpected("BRIGHT BLACK", makeSimpleCtrlSeq(100))
            .addExpected("BRIGHT RED", makeSimpleCtrlSeq(101))
            .addExpected("BRIGHT GREEN", makeSimpleCtrlSeq(102))
            .addExpected("BRIGHT YELLOW", makeSimpleCtrlSeq(103))
            .addExpected("BRIGHT BLUE", makeSimpleCtrlSeq(104))
            .addExpected("BRIGHT MAGENTA", makeSimpleCtrlSeq(105))
            .addExpected("BRIGHT CYAN", makeSimpleCtrlSeq(106))
            .addExpected("BRIGHT WHITE", makeSimpleCtrlSeq(107))
    )

    fun `test multiple attributes 1`() = check(
        ColoredText(
            makeSimpleCtrlSeq(0) +
                make24BitColorCtrlSeq(0, 0, 0, isForeground = false) +
                make8BitColorCtrlSeq(255, isForeground = true) +
                "TEXT"
        ).addExpected("TEXT", "${CSI}0;40;${if (SystemInfo.isWindows) 30 else 97}m")
    )

    fun `test multiple attributes 2`() = check(
        ColoredText("${CSI}0m${CSI}48;$ANSI_24_BIT_COLOR_FORMAT;0;0;0m${CSI}38;$ANSI_8_BIT_COLOR_FORMAT;255mTEXT")
            .addExpected("TEXT", "${CSI}0;40;${if (SystemInfo.isWindows) 30 else 97}m")
    )

    private class ColoredText(val rawText: String, val outputType: Key<*> = ProcessOutputTypes.STDOUT) {
        val expectedColoredChunks: MutableList<Pair<String, String>> = mutableListOf()

        fun addExpected(text: String, colorKey: String): ColoredText {
            expectedColoredChunks.add(Pair(text, colorKey))
            return this
        }
    }

    companion object {
        private fun check(text: ColoredText) {
            val decoder = RsAnsiEscapeDecoder()
            val actualColoredChunks = mutableListOf<Pair<String, String>>()
            val acceptor = AnsiEscapeDecoder.ColoredTextAcceptor { s, attrs ->
                actualColoredChunks.add(Pair(s, attrs.toString()))
            }
            decoder.escapeText(text.rawText, text.outputType, acceptor)
            val expectedColoredChunks = mutableListOf<Pair<String, String>>()
            expectedColoredChunks.addAll(text.expectedColoredChunks)
            assertEquals(expectedColoredChunks, actualColoredChunks)
        }

        private fun makeSimpleCtrlSeq(n: Int): String = "$CSI${n}m"

        private fun make24BitColorCtrlSeq(r: Int, g: Int, b: Int, isForeground: Boolean): String =
            "$CSI${if (isForeground) "38;" else "48;"}$ANSI_24_BIT_COLOR_FORMAT;$r;$g;${b}m"

        private fun make8BitColorCtrlSeq(n: Int, isForeground: Boolean): String =
            "$CSI${if (isForeground) "38;" else "48;"}$ANSI_8_BIT_COLOR_FORMAT;${n}m"
    }
}
