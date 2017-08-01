/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.psi.PsiElement
import org.assertj.core.api.Assertions
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsMethodCallExpr


class RsInlayParameterHintsProviderTest : RsTestBase() {

    fun testFnOneArg() = checkByText<RsCallExpr>("""
        fn foo(arg: u32) {}
        fn main() { foo(<caret>0); }
                    //^
    """, "arg:", 0)

    fun testFnTwoArg() = checkByText<RsCallExpr>("""
        fn foo(arg: u32, arg2: u32) {}
        fn main() { foo(0, <caret>1); }
                    //^
    """, "arg2:", 1)

    fun `test arg out of bounds`() = checkByText<RsCallExpr>("""
        fn foo(arg: u32) {}
        fn main() { foo(0, <caret>1); }
                    //^
    """, "<none>", -1)

    fun testMethodTwoArg() = checkByText<RsMethodCallExpr>("""
        struct S;
        impl S {
            fn foo(self, arg: u32, arg2: u32) {}
        }
        fn main() {
            let s = S;
            s.foo(0, <caret>1);
        }    //^
    """, "arg2:", 1)

    fun testStructFnArg() = checkByText<RsCallExpr>("""
        struct S;
        impl S {
            fn foo(self, arg: u32) {}
        }
        fn main() {
            let s = S;
            S::foo(s, <caret>0);
        }    //^
    """, "arg:", 1)

    fun testLetDecl() = checkByText<RsLetDecl>("""
        struct S;
        fn main() {
            let s<caret> = S;
        }  //^
    """, ": S", 0)


    inline private fun <reified T : PsiElement> checkByText(@Language("Rust") code: String, hint: String, pos: Int) {
        myFixture.configureByText("main.rs", code)
        val target = findElementInEditor<T>("^")
        val inlays = RsInlayParameterHintsProvider().getParameterHints(target)
        if (pos != -1) {
            check(pos < inlays.size) {
                "Expected at least ${pos + 1} hints, got ${inlays.map { it.text }}"
            }
            Assertions.assertThat(inlays[pos].text).isEqualTo(hint)
            Assertions.assertThat(inlays[pos].offset).isEqualTo(myFixture.editor.caretModel.offset)
        }
    }
}
