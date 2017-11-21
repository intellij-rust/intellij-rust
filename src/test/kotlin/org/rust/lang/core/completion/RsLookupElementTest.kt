/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.RsFile

class RsLookupElementTest : RsTestBase() {
    private val `$` = '$'
    fun `test fn`() = check("""
        fn foo(x: i32) -> Option<String> {}
          //^
    """, tailText = "(x: i32)", typeText = "Option<String>")

    fun `test trait method`() = check("""
        trait T {
            fn foo(&self, x: i32) {}
              //^
        }
    """, tailText = "(&self, x: i32)", typeText = "()")

    fun `test trait by method`() = check("""
        trait T {
            fn foo(&self, x: i32);
        }
        struct S;
        impl T for S {
            fn foo(&self, x: i32) {
              //^
                unimplemented!()
            }
        }
    """, tailText = "(&self, x: i32) of T", typeText = "()")

    fun `test cons item`() = check("""
        const C: S = unimplemented!();
            //^
    """, typeText = "S")

    fun `test static item`() = check("""
        static C: S = unimplemented!();
             //^
    """, typeText = "S")

    fun `test tuple struct`() = check("""
        struct S(f32, i64);
             //^
    """, tailText = "(f32, i64)")

    fun `test struct`() = check("""
        struct S { field: String }
             //^
    """, tailText = " { ... }")

    fun `test enum`() = check("""
        enum E { X, Y }
           //^
    """)

    fun `test enum struct variant`() = check("""
        enum E { X {} }
               //^
    """, tailText = " { ... }", typeText = "E")

    fun `test enum tuple variant`() = check("""
        enum E { X(i32, String) }
               //^
    """, tailText = "(i32, String)", typeText = "E")

    fun `test field`() = check("""
        struct S { field: String }
                   //^
    """, typeText = "String")

    fun `test macro simple`() = check("""
        macro_rules! test {
            ($`$`test:expr) => ($`$`test)
                //^
        }
    """, tailText = null, typeText = "expr")

    fun `test macro definition`() = check("""
        macro_rules! test { () => () }
                     //^
    """, tailText = "!", typeText = null)

    fun `test mod`() {
        myFixture.configureByText("foo.rs", "")
        val lookup = createLookupElement((myFixture.file as RsFile), "foo")
        val presentation = LookupElementPresentation()

        lookup.renderElement(presentation)
        check(presentation.icon != null)
        check(presentation.itemText == "foo")
    }

    private fun check(@Language("Rust") code: String, tailText: String? = null, typeText: String? = null) {
        InlineFile(code)
        val element = findElementInEditor<RsNamedElement>()
        val lookup = createLookupElement(element, element.name!!)
        val presentation = LookupElementPresentation()

        lookup.renderElement(presentation)
        check(presentation.icon != null)
        check(presentation.tailText == tailText)
        check(presentation.typeText == typeText)
    }
}
