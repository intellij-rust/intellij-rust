/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.RsFile

class RsLookupElementTest : RsTestBase() {
    private val `$` = '$'
    fun testFn() = check("""
        fn foo(x: i32) -> Option<String> {}
          //^
    """, tailText = "(x: i32)", typeText = "Option<String>")

    fun testTraitMethod() = check("""
        trait T {
            fn foo(&self, x: i32) {}
              //^
        }
    """, tailText = "(&self, x: i32)", typeText = "()")

    fun testTraitByMethod() = check("""
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

    fun testConsItem() = check("""
        const C: S = unimplemented!();
            //^
    """, typeText = "S")

    fun testStaticItem() = check("""
        static C: S = unimplemented!();
             //^
    """, typeText = "S")

    fun testTupleStruct() = check("""
        struct S(f32, i64);
             //^
    """, tailText = "(f32, i64)")

    fun testStruct() = check("""
        struct S { field: String }
             //^
    """, tailText = " { ... }")

    fun testEnum() = check("""
        enum E { X, Y }
           //^
    """)

    fun testEnumStructVariant() = check("""
        enum E { X {} }
               //^
    """, tailText = " { ... }", typeText = "E")

    fun testEnumTupleVariant() = check("""
        enum E { X(i32, String) }
               //^
    """, tailText = "(i32, String)", typeText = "E")

    fun testField() = check("""
        struct S { field: String }
                   //^
    """, typeText = "String")

    fun `test macro simple`() = check("""
        macro_rules! test {
            ($`$`test:expr) => ($`$`test)
                //^
        }
    """, tailText = null, typeText = "expr")

    fun `test marco definition`() = check("""
        macro_rules! test { () => () }
                     //^
    """, tailText = "!", typeText = null)

    fun testMod() {
        myFixture.configureByText("foo.rs", "")
        val lookup = createLookupElement((myFixture.file as RsFile), "foo")
        val presentation = LookupElementPresentation()

        lookup.renderElement(presentation)
        assertThat(presentation.icon).isNotNull()
        assertThat(presentation.itemText).isEqualTo("foo")
    }

    private fun check(@Language("Rust") code: String, tailText: String? = null, typeText: String? = null) {
        InlineFile(code)
        val element = findElementInEditor<RsNamedElement>()
        val lookup = createLookupElement(element, element.name!!)
        val presentation = LookupElementPresentation()

        lookup.renderElement(presentation)
        assertThat(presentation.icon).isNotNull()
        assertThat(presentation.tailText).isEqualTo(tailText)
        assertThat(presentation.typeText).isEqualTo(typeText)
    }

}
