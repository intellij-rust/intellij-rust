/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.parentOfType
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.RsInferenceContextOwner
import org.rust.lang.core.types.regions.Scope
import org.rust.lang.core.types.regions.ScopeTree
import org.rust.lang.core.types.regions.getRegionScopeTree
import org.rust.lang.core.types.regions.span

/**
 * `//^`       - mark for pat bindings. Used to calculate LCA of all scopes of marked bindings.
 * `/*start*/` - start-of-expected-scope mark.
 * `/*end*/`   - end-of-expected-scope mark.
 */
class RsScopeTest : RsTestBase() {

    fun `test scope of binding is block remainder`() = doTest("""
        fn foo() -> i32 {
            let a = 42;/*start*/
              //^
        }/*end*/
    """)

    fun `test scope of binding is (nested) block remainder`() = doTest("""
        fn foo() -> i32 {
            {
                let a = 42;/*start*/
                  //^
                a
            }/*end*/
        }
    """)

    fun `test scope of binding is subscope to prev binding`() = doTest("""
        fn foo() -> i32 {
            let a = 42;/*start*/
              //^
            let b = 24;
              //^
            a + b
        }/*end*/
    """)

    fun `test scope of nested block is sibling scope to next binding`() = doTest("""
        fn foo() -> i32 /*start*/{
            {
                let a = 42;
                  //^
            }
            let b = 24;
              //^
            b
        }/*end*/
    """)

    fun `test parameter scope is fn body scope`() = doTest("""
        fn foo(p: i32) -> i32 /*start*/{
             //^
            let a = 42;
            a
        }/*end*/
    """)

    fun `test parameters scope is a supscope to stmt scope`() = doTest("""
        fn foo(x: i32) -> i32 /*start*/{
             //^
            let a = 42;
              //^
            a
        }/*end*/
    """)

    fun `test tuple let binding`() = doTest("""
        fn foo() -> i32 {
            let (a, b) = (42, 42);/*start*/
                  //^
            a + b
        }/*end*/
    """)

    fun `test struct let binding`() = doTest("""
        struct A { x: i32 }
        fn foo() -> i32 {
            let a = A { x: 42 };
            let A { x } = A;/*start*/
                  //^
            x
        }/*end*/
    """)

    fun `test match arm scope is match expr scope`() = doTest("""
        fn foo() -> i32 {
            let x = 42;
   /*start*/match x {
                y => { 42 }
              //^
            }/*end*/
        }
    """)

    fun `test binding in struct field initializer`() = doTest("""
        struct S { f: i32 }
        fn foo() -> i32 {
            let a = S {
                f: {
                    let x = 42;/*start*/
                      //^
                    x
                }/*end*/
            };
            a.f
        }
    """)

    fun `test binding in const initializer`() = doTest("""
        const X: i32 = {
            let x = 42;/*start*/
              //^
            x
        }/*end*/;
    """)

    fun `test binding in array type`() = doTest("""
        struct S {
            f: [u8; { 1 } + {
                let a = 42;/*start*/
                  //^
                a
            }/*end*/ + { 1 }]
        }
    """)

    fun `test binding in enum variant`() = doTest("""
        enum Foo {
            BAR = {
                let a = 42;/*start*/
                  //^
                a
            }/*end*/
        }
    """)

    private fun doTest(@Language("Rust") code: String) {
        val scopeStartOffset = findMarker(code, START_MARKER)
        val scopeEndOffset = findMarker(code, END_MARKER)
        check(scopeStartOffset < scopeEndOffset) { "`$START_MARKER` occurs before `$END_MARKER`" }
        val expectedSpan = TextRange(scopeStartOffset, scopeEndOffset - START_MARKER.length)

        val text = code
            .removeRange(scopeEndOffset, scopeEndOffset + END_MARKER.length)
            .removeRange(scopeStartOffset, scopeStartOffset + START_MARKER.length)
        InlineFile(text)

        val patAndDataAndOffsets = findElementsWithDataAndOffsetInEditor<RsPatBinding>()
        val scopeWithScopeTrees = mutableListOf<Pair<Scope, ScopeTree>>()
        for ((pat, data, _) in patAndDataAndOffsets) {
            check(data.isEmpty()) { "Did not expect marker data" }
            val contextOwner = checkNotNull(pat.parentOfType<RsInferenceContextOwner>()) { "Cannot find pat owner" }
            val scopeTree = getRegionScopeTree(contextOwner)
            val scope = checkNotNull(scopeTree.getVariableScope(pat)) { "Cannot infer scope of pat: `${pat.text}`" }
            scopeWithScopeTrees.add(Pair(scope, scopeTree))
        }
        val (scope, _) = scopeWithScopeTrees.reduce { (scope1, scopeTree1), (scope2, scopeTree2) ->
            check(scopeTree1 == scopeTree2) { "Scopes refer to different scope trees" }
            val scopesCommonAncestor = scopeTree1.getLowestCommonAncestor(scope1, scope2)
            Pair(scopesCommonAncestor, scopeTree1)
        }
        val actualSpan = scope.span

        assertEquals(expectedSpan, actualSpan)
    }

    private fun findMarker(text: String, marker: String): Int {
        val markerOffset = text.indexOf(marker)
        check(markerOffset != -1) { "No `$marker` marker:\n$text" }
        check(text.indexOf(marker, startIndex = markerOffset + 1) == -1) {
            "More than one `$marker` marker:\n$text"
        }
        return markerOffset
    }

    companion object {
        private const val START_MARKER = "/*start*/"
        private const val END_MARKER = "/*end*/"
    }
}
