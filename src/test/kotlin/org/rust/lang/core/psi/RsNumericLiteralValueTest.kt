package org.rust.lang.core.psi

import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.lang.core.psi.RsTokenElementTypes.FLOAT_LITERAL
import org.rust.lang.core.psi.RsTokenElementTypes.INTEGER_LITERAL
import org.rust.lang.core.psi.impl.RsNumericLiteralImpl
import java.util.*

@RunWith(Parameterized::class)
class RsNumericLiteralValueTest(private val constructor: (String) -> RsLiteral.Number,
                                private val input: String,
                                private val expectedOutput: Any?) {
    @Test
    fun test() {
        val elem = constructor(input)
        assertEquals(expectedOutput, if (elem.isInt) elem.valueAsLong else elem.valueAsDouble)
    }

    companion object {
        @Parameterized.Parameters(name = "{index}: {1}")
        @JvmStatic fun data(): Collection<Array<out Any?>> = listOf(
            arrayOf(u64, "12345", 12345L),
            arrayOf(u64, "1_2_3_4_5", 12345L),
            arrayOf(f64, "1.0", 1.0),
            arrayOf(f64, "1.0_1", 1.01),
            arrayOf(f64, "2.4e8", 2.4e8),
            arrayOf(f64, "10_E-6", 10e-6),
            arrayOf(u64, "0x1F", 31L),
            arrayOf(u64, "0o17", 15L),
            arrayOf(u64, "0b1001", 9L),
            arrayOf(f64, "9.", 9.0),
            arrayOf(u64, "abrakadabra", null),
            arrayOf(u64, "", null),
            arrayOf(u64, "0x1${"0".repeat(15)}1", null) // 2^64+1
        )

        val u64 = { s: String -> RsNumericLiteralImpl(INTEGER_LITERAL, s) }
        val f64 = { s: String -> RsNumericLiteralImpl(FLOAT_LITERAL, s) }
    }
}

class RsNumericLiteralValueFuzzyTest {
    @Test
    fun testFuzzyIntegers() {
        repeat(10000) {
            doTest(INTEGER_LITERAL, randomLiteral())
        }
    }

    @Test
    fun testFuzzyFloats() {
        repeat(10000) {
            doTest(FLOAT_LITERAL, randomLiteral())
        }
    }

    private fun doTest(elementType: IElementType, text: String) {
        try {
            val elem = RsNumericLiteralImpl(elementType, text)
            if (elem.isInt) {
                elem.valueAsLong
            } else {
                elem.valueAsDouble
            }
        } catch(e: Exception) {
            fail("exception thrown by $text")
        }
    }

    private fun randomLiteral(): String {
        val random = Random()
        val length = random.nextInt(10)
        val chars = "0123456789abcdefABCDEFxo._eE-+"
        val xs = CharArray(length, {
            chars[random.nextInt(chars.length)]
        })
        return String(xs)
    }
}
