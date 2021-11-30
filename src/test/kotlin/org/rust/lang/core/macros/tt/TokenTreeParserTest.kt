/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import org.rust.RsTestBase
import org.rust.lang.core.parser.createRustPsiBuilder

class TokenTreeParserTest : RsTestBase() {
    fun `test 0`() = doTest(".", """
        SUBTREE $
          PUNCT   . [alone] 0
    """)

    fun `test 1`() = doTest("..", """
        SUBTREE $
          PUNCT   . [joint] 0
          PUNCT   . [alone] 1
    """)

    fun `test 2`() = doTest("...", """
        SUBTREE $
          PUNCT   . [joint] 0
          PUNCT   . [joint] 1
          PUNCT   . [alone] 2
    """)

    fun `test 3`() = doTest(".. .", """
        SUBTREE $
          PUNCT   . [joint] 0
          PUNCT   . [alone] 1
          PUNCT   . [alone] 2
    """)

    fun `test 4`() = doTest(".foo", """
        SUBTREE $
          PUNCT   . [alone] 0
          IDENT   foo 1
    """)

    fun `test 5`() = doTest(":::", """
        SUBTREE $
          PUNCT   : [joint] 0
          PUNCT   : [joint] 1
          PUNCT   : [alone] 2
    """)

    fun `test 6`() = doTest("()", """
        SUBTREE () 0
    """)

    fun `test 7`() = doTest("{}", """
        SUBTREE {} 0
    """)

    fun `test 8`() = doTest("[]", """
        SUBTREE [] 0
    """)

    fun `test 9`() = doTest("""."foo"""", """
        SUBTREE $
          PUNCT   . [alone] 0
          LITERAL "foo" 1
    """)

    fun `test 10`() = doTest(""".r"foo"""", """
        SUBTREE $
          PUNCT   . [alone] 0
          LITERAL r"foo" 1
    """)

    fun `test 11`() = doTest(""".r#"foo"#""", """
        SUBTREE $
          PUNCT   . [alone] 0
          LITERAL r#"foo"# 1
    """)

    fun `test 12`() = doTest("1", """
        SUBTREE $
          LITERAL 1 0
    """)

    fun `test 13`() = doTest("-1", """
        SUBTREE $
          PUNCT   - [alone] 0
          LITERAL 1 1
    """)

    fun `test 14`() = doTest("1i32", """
        SUBTREE $
          LITERAL 1i32 0
    """)

    fun `test 15`() = doTest("1f32", """
        SUBTREE $
          LITERAL 1f32 0
    """)

    fun `test 16`() = doTest("1.2", """
        SUBTREE $
          LITERAL 1.2 0
    """)

    fun `test 17`() = doTest("1.2 ", """
        SUBTREE $
          LITERAL 1.2 0
    """)

    fun `test 18`() = doTest("1.2f64", """
        SUBTREE $
          LITERAL 1.2f64 0
    """)

    fun `test 19`() = doTest("1.2e-1", """
        SUBTREE $
          LITERAL 1.2e-1 0
    """)

    fun `test 20`() = doTest("'f'", """
        SUBTREE $
          LITERAL 'f' 0
    """)

    fun `test 21`() = doTest("'foo", """
        SUBTREE $
          PUNCT   ' [joint] 0
          IDENT   foo 1
    """)

    fun `test 22`() = doTest("'foo 'bar", """
        SUBTREE $
          PUNCT   ' [joint] 0
          IDENT   foo 1
          PUNCT   ' [joint] 2
          IDENT   bar 3
    """)

    fun `test 23`() = doTest(".(.).", """
        SUBTREE $
          PUNCT   . [alone] 0
          SUBTREE () 1
            PUNCT   . [joint] 2
          PUNCT   . [alone] 3
    """)

    fun `test 24`() = doTest(".(.{.[].}.)", """
        SUBTREE $
          PUNCT   . [alone] 0
          SUBTREE () 1
            PUNCT   . [alone] 2
            SUBTREE {} 3
              PUNCT   . [alone] 4
              SUBTREE [] 5
              PUNCT   . [joint] 6
            PUNCT   . [joint] 7
    """)

    fun `test 25`() = doTest(". ( . { . [ ] . } . ) .", """
        SUBTREE $
          PUNCT   . [alone] 0
          SUBTREE () 1
            PUNCT   . [alone] 2
            SUBTREE {} 3
              PUNCT   . [alone] 4
              SUBTREE [] 5
              PUNCT   . [alone] 6
            PUNCT   . [alone] 7
          PUNCT   . [alone] 8
    """)

    fun `test 26`() = doTest("(", """
        SUBTREE $
          PUNCT   ( [alone] 1
    """)

    fun `test 27`() = doTest("(.", """
        SUBTREE $
          PUNCT   ( [alone] 2
          PUNCT   . [alone] 1
    """)

    fun `test 28`() = doTest(")", """
        SUBTREE $
          PUNCT   ) [alone] 0
    """)

    fun `test 29`() = doTest(".)", """
        SUBTREE $
          PUNCT   . [joint] 0
          PUNCT   ) [alone] 1
    """)

    fun `test 30`() = doTest("{(.}", """
        SUBTREE $
          PUNCT   { [alone] 5
          PUNCT   ( [alone] 4
          PUNCT   . [joint] 2
          PUNCT   } [alone] 3
    """)

    fun `test 31`() = doTest("{.)}", """
        SUBTREE {} 0
          PUNCT   . [joint] 1
          PUNCT   ) [joint] 2
    """)

    fun `test 32`() = doTest(".'foo", """
        SUBTREE $
          PUNCT   . [alone] 0
          PUNCT   ' [joint] 1
          IDENT   foo 2
    """)

    fun `test 33`() = doTest("_", """
        SUBTREE $
          IDENT   _ 0
    """)

    fun `test 34`() = doTest("._", """
        SUBTREE $
          PUNCT   . [alone] 0
          IDENT   _ 1
    """)

    fun `test 35`() = doTest("1.0 foo bar", """
        SUBTREE $
          LITERAL 1.0 0
          IDENT   foo 1
          IDENT   bar 2
    """)

    fun doTest(code: String, expected: String) {
        val subtree = project.createRustPsiBuilder(code).parseSubtree().subtree
        assertEquals(subtree, FlatTree.fromSubtree(subtree).toTokenTree())
        assertEquals(expected.trimIndent(), subtree.toDebugString())
    }
}
