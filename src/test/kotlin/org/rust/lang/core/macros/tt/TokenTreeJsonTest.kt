/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.macros.proc.ProMacroExpanderVersion
import org.rust.lang.core.macros.proc.ProcMacroJsonParser
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.test.util.RsTestJsonPrettyPrinter

class TokenTreeJsonTest : RsTestBase() {
    fun `test 1`() = doTest(".", """
        {
          "subtree": [ 4294967295, 4294967295, 0, 0, 1 ],
          "literal": [ ],
          "punct": [ 0, 46, 0 ],
          "ident": [ ],
          "token_tree": [ 2 ],
          "text": [ ]
        }
    """)

    fun `test 2`() = doTest("..", """
        {
          "subtree": [ 4294967295, 4294967295, 0, 0, 2 ],
          "literal": [ ],
          "punct": [ 0, 46, 1, 1, 46, 0 ],
          "ident": [ ],
          "token_tree": [ 2, 6 ],
          "text": [ ]
        }
    """)

    fun `test 3`() = doTest(".foo", """
        {
          "subtree": [ 4294967295, 4294967295, 0, 0, 2 ],
          "literal": [ ],
          "punct": [ 0, 46, 0 ],
          "ident": [ 1, 0 ],
          "token_tree": [ 2, 3 ],
          "text": [ "foo" ]
        }
    """)

    fun `test 4`() = doTest(":::", """
        {
          "subtree": [ 4294967295, 4294967295, 0, 0, 3 ],
          "literal": [ ],
          "punct": [ 0, 58, 1, 1, 58, 1, 2, 58, 0 ],
          "ident": [ ],
          "token_tree": [ 2, 6, 10 ],
          "text": [ ]
        }
    """)

    fun `test 5`() = doTest(". asd .. \"asd\" ...", """
        {
          "subtree": [ 4294967295, 4294967295, 0, 0, 8 ],
          "literal": [ 4, 1 ],
          "punct": [ 0, 46, 0, 2, 46, 1, 3, 46, 0, 5, 46, 1, 6, 46, 1, 7, 46, 0 ],
          "ident": [ 1, 0 ],
          "token_tree": [ 2, 3, 6, 10, 1, 14, 18, 22 ],
          "text": [ "asd", "\"asd\"" ]
        }
    """)

    fun `test 6`() = doTest("{}", """
        {
          "subtree": [ 0, 4294967295, 2, 0, 0 ],
          "literal": [ ],
          "punct": [ ],
          "ident": [ ],
          "token_tree": [ ],
          "text": [ ]
        }
    """)

    fun `test 7`() = doTest("[]", """
        {
          "subtree": [ 0, 4294967295, 3, 0, 0 ],
          "literal": [ ],
          "punct": [ ],
          "ident": [ ],
          "token_tree": [ ],
          "text": [ ]
        }
    """)

    fun `test 8`() = doTest("()", """
        {
          "subtree": [ 0, 4294967295, 1, 0, 0 ],
          "literal": [ ],
          "punct": [ ],
          "ident": [ ],
          "token_tree": [ ],
          "text": [ ]
        }
    """)

    fun `test 9`() = doTest("(..)", """
        {
          "subtree": [ 0, 4294967295, 1, 0, 2 ],
          "literal": [ ],
          "punct": [ 1, 46, 1, 2, 46, 1 ],
          "ident": [ ],
          "token_tree": [ 2, 6 ],
          "text": [ ]
        }
    """)

    fun `test 10`() = doTest("([{.}])", """
        {
          "subtree": [ 0, 4294967295, 1, 0, 1, 1, 4294967295, 3, 1, 2, 2, 4294967295, 2, 2, 3 ],
          "literal": [ ],
          "punct": [ 3, 46, 1 ],
          "ident": [ ],
          "token_tree": [ 4, 8, 2 ],
          "text": [ ]
        }
    """)

    fun doTest(code: String, @Language("Json") expectedJson: String) {
        val subtree = project.createRustPsiBuilder(code).parseSubtree().subtree

        val version = ProMacroExpanderVersion.ENCODE_CLOSE_SPAN_VERSION

        val jackson = ProcMacroJsonParser.JSON_MAPPER
        val actualJson = jackson
            .writer()
            .with(DefaultPrettyPrinter(RsTestJsonPrettyPrinter()))
            .writeValueAsString(FlatTree.fromSubtree(subtree, version))

        assertEquals(expectedJson.trimIndent(), actualJson)
        assertEquals(jackson.readValue(actualJson, FlatTree::class.java).toTokenTree(version), subtree)
    }
}
