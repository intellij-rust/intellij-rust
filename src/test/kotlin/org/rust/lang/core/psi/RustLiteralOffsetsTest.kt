package org.rust.lang.core.psi

import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.lang.core.psi.impl.RustNumericLiteralImpl
import org.rust.lang.core.psi.impl.RustRawStringLiteralImpl
import org.rust.lang.core.psi.impl.RustStringLiteralImpl
import org.rust.lang.core.psi.RustTokenElementTypes.BYTE_LITERAL as BCH
import org.rust.lang.core.psi.RustTokenElementTypes.CHAR_LITERAL as CHR
import org.rust.lang.core.psi.RustTokenElementTypes.FLOAT_LITERAL as FLT
import org.rust.lang.core.psi.RustTokenElementTypes.INTEGER_LITERAL as INT
import org.rust.lang.core.psi.RustTokenElementTypes.RAW_BYTE_STRING_LITERAL as BRW
import org.rust.lang.core.psi.RustTokenElementTypes.RAW_STRING_LITERAL as RAW

abstract class RustLiteralOffsetsTestCase(
    private val type: IElementType,
    private val text: String,
    private val constructor: (IElementType, CharSequence) -> RustLiteral) {

    protected fun doTest() {
        val literal = constructor(type, text.replace("|", ""))
        val expected = makeOffsets(text)
        assertEquals(expected, literal.offsets)
    }

    private fun makeOffsets(text: String): RustLiteral.Offsets {
        val parts = text.split('|')
        assert(parts.size == 5)
        val prefixEnd = parts[0].length
        val openDelimEnd = prefixEnd + parts[1].length
        val valueEnd = openDelimEnd + parts[2].length
        val closeDelimEnd = valueEnd + parts[3].length
        val suffixEnd = closeDelimEnd + parts[4].length
        return RustLiteral.Offsets.fromEndOffsets(prefixEnd, openDelimEnd, valueEnd, closeDelimEnd, suffixEnd)
    }
}

@RunWith(Parameterized::class)
class RustNumericLiteralOffsetsTest(
    type: IElementType,
    text: String
) : RustLiteralOffsetsTestCase(type, text, ::RustNumericLiteralImpl) {

    @Test
    fun test() = doTest()

    companion object {
        @Parameterized.Parameters(name = "{index}: {1}")
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            arrayOf(INT, "||123||i32"),
            arrayOf(INT, "||0||u"),
            arrayOf(INT, "||0||"),
            arrayOf(FLT, "||-12||f32"),
            arrayOf(FLT, "||-12.124||f32"),
            arrayOf(FLT, "||1.0e10||"),
            arrayOf(FLT, "||1.0e||"),
            arrayOf(FLT, "||1.0e||e"),
            arrayOf(INT, "||0xABC||"),
            arrayOf(INT, "||0xABC||i64"),
            arrayOf(FLT, "||2.||"),
            arrayOf(INT, "||0x||"),
            arrayOf(INT, "||1_______||"),
            arrayOf(INT, "||1_______||i32")
        )
    }
}

@RunWith(Parameterized::class)
class RustStringLiteralOffsetsTest(type: IElementType, text: String) :
    RustLiteralOffsetsTestCase(type, text, ::RustStringLiteralImpl) {
    @Test
    fun test() = doTest()

    companion object {
        @Parameterized.Parameters(name = "{index}: {1}")
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            arrayOf(CHR, "|'|a|'|suf"),
            arrayOf(BCH, "b|'|a|'|"),
            arrayOf(BCH, "b|'|a|'|suf"),
            arrayOf(CHR, "|'|a||"),
            arrayOf(CHR, "|'||'|"),
            arrayOf(CHR, "|'|\\\\|'|"),
            arrayOf(CHR, "|'|\\'||"),
            arrayOf(CHR, "|'||'|a"),
            arrayOf(CHR, "|'|\\\\|'|a"),
            arrayOf(CHR, "|'|\\'a||"),
            arrayOf(CHR, "|'|\\\\\\'a||")
        )
    }
}

@RunWith(Parameterized::class)
class RustRawStringLiteralOffsetsTest(type: IElementType, text: String) :
    RustLiteralOffsetsTestCase(type, text, ::RustRawStringLiteralImpl) {
    @Test
    fun test() = doTest()

    companion object {
        @Parameterized.Parameters(name = "{index}: {1}")
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            arrayOf(RAW, "r|\"|a|\"|suf"),
            arrayOf(BRW, "br|\"|a|\"|suf"),
            arrayOf(RAW, "r|\"|a|\"|"),
            arrayOf(RAW, "r|###\"|aaa||"),
            arrayOf(RAW, "r|###\"|aaa\"##||"),
            arrayOf(RAW, "r|###\"||\"###|"),
            arrayOf(RAW, "r|###\"|a\"##a|\"###|s")
        )
    }
}
