/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import junit.framework.ComparisonFailure
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.graph

class RsMacroGraphBuilderTest : RsTestBase() {
    private fun check(@Language("Rust") code: String, expectedIndented: String) {
        InlineFile(code)
        val macro = myFixture.file.descendantsOfType<RsMacro>().single()
        val graph = macro.graph!!
        // println(graph.createDotDescription())
        val expected = expectedIndented.trimIndent()
        val actual = graph.depthFirstTraversalTrace()
        check(actual == expected) { throw ComparisonFailure("Comparision failed", expected, actual) }
    }

    fun `test one rule simple`() = check("""
        macro_rules! my_macro {
            ($ e:expr) => (1);
        }
    """, """
        START
        [S]
        Expr
        [E]
        END
    """)

    fun `test one rule ?`() = check("""
        macro_rules! my_macro {
            ($ ($ id:ident $ e:expr)? ) => (1);
        }
    """, """
        START
        [S]
        [S]
        Ident
        Expr
        [E]
        [E]
        END
    """)

    fun `test one rule repetition *`() = check("""
        macro_rules! my_macro {
            ($ ($ id:ident),* ) => (1);
        }
    """, """
        START
        [S]
        [S]
        [E]
        Ident
        [S]
        ,
        [E]
        [E]
        END
    """)

    fun `test one rule repetition +`() = check("""
        macro_rules! my_macro {
            ($ ($ id:ident),+ ) => (1);
        }
    """, """
        START
        [S]
        [E]
        Ident
        [S]
        ,
        [E]
        END
    """)

    fun `test one rule parens`() = check("""
        macro_rules! my_macro {
            ($ i:ident($ t:ty)) => (1);
        }
    """, """
        START
        [S]
        Ident
        (
        Ty
        )
        [E]
        END
    """)

    fun `test many rules simple`() = check("""
        macro_rules! my_macro {
            ($ e:expr) => (1);
            ($ e:ident) => (2);
            ($ x:ty) => (3);
            ($ x:literal) => (4);
        }
    """, """
        START
        [S]
        Expr
        [E]
        END
        Ident
        Ty
        Literal
    """)

    fun `test many rules complex`() = check("""
        macro_rules! my_macro {
            ($ e:expr) => (1);
            ($ ($ id:ident)+ ) => (2);
            ($ x:expr, $ y:ident) => (2);
            ($ x:literal, ABC $ y:expr) => (4);
        }
    """, """
        START
        [S]
        Expr
        [E]
        END
        [E]
        Ident
        [S]
        Expr
        ,
        Ident
        Literal
        ,
        ABC
        Expr
    """)
}
