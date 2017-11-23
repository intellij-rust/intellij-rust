/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.lang.core.psi.RsElementTypes.FLOAT_LITERAL
import java.util.*

@RunWith(Parameterized::class)
class RsNumericLiteralValueTest(private val constructor: (String) -> RsLiteralKind.Float,
                                private val input: String,
                                private val expectedOutput: Any?) {
    @Test
    fun test() {
        val elem = constructor(input)
        assertEquals(expectedOutput, elem.value)
    }

    companion object {
        @Parameterized.Parameters(name = "{index}: {1}")
        @JvmStatic fun data(): Collection<Array<out Any?>> = listOf(
            arrayOf(f64, "1.0", 1.0),
            arrayOf(f64, "1.0_1", 1.01),
            arrayOf(f64, "2.4e8", 2.4e8),
            arrayOf(f64, "10_E-6", 10e-6),
            arrayOf(f64, "9.", 9.0)
        )

        val f64 = { s: String -> RsLiteralKind.Float(LeafPsiElement(FLOAT_LITERAL, s)) }
    }
}

class RsNumericLiteralValueFuzzyTest {
    @Test
    fun `test fuzzy floats`() {
        repeat(10000) {
            doTest(randomLiteral())
        }
    }

    private fun doTest(text: String) {
        try {
            RsLiteralKind.Float(LeafPsiElement(FLOAT_LITERAL, text)).value
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
