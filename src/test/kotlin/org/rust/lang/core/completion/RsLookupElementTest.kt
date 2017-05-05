package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.RsFile

class RsLookupElementTest : RsTestBase() {
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
        const c: S = unimplemented!();
            //^
    """, typeText = "S")

    fun testStaticItem() = check("""
        static c: S = unimplemented!();
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

    fun testMod() {
        myFixture.configureByText("foo.rs", "")
        val lookup = (myFixture.file as RsFile).createLookupElement("foo")
        val presentation = LookupElementPresentation()

        lookup.renderElement(presentation)
        assertThat(presentation.icon).isNotNull()
        assertThat(presentation.itemText).isEqualTo("foo")
    }

    private fun check(@Language("Rust") code: String, tailText: String? = null, typeText: String? = null) {
        InlineFile(code)
        val element = findElementInEditor<RsNamedElement>()
        val lookup = element.createLookupElement(element.name!!)
        val presentation = LookupElementPresentation()

        lookup.renderElement(presentation)
        assertThat(presentation.icon).isNotNull()
        assertThat(presentation.tailText).isEqualTo(tailText)
        assertThat(presentation.typeText).isEqualTo(typeText)
    }

}
