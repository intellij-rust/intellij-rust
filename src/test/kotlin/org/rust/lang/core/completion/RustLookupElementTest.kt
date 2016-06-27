package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCaseBase
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.parentOfType

class RustLookupElementTest : RustTestCaseBase() {
    override val dataPath: String get() = ""

    fun testFn() = check(
        //language=Rust
        """
        fn foo(x: i32) -> Option<String> {}
          //^
    """, tailText = "(x: i32)", typeText = "Option<String>")

    fun testTraitMethod() = check(
        //language=Rust
        """
        trait T {
            fn foo(&self, x: i32) {}
              //^
        }
    """, tailText = "(&self, x: i32)", typeText = "()")

    fun testConsItem() = check(
        //language=Rust
        """
        const c: S = unimplemented!();
            //^
    """, typeText = "S")

    fun testStaticItem() = check(
        //language=Rust
        """
        static c: S = unimplemented!();
             //^
    """, typeText = "S")

    fun testTupleStruct() = check(
        //language=Rust
        """
        struct S(f32, i64);
             //^
    """, tailText = "(f32, i64)")

    fun testStruct() = check(
        //language=Rust
        """
        struct S { field: String }
             //^
    """, tailText = " { ... }")

    fun testEnum() = check(
        //language=Rust
        """
        enum E { X, Y }
           //^
    """)

    fun testEnumStructVariant() = check(
        //language=Rust
        """
        enum E { X {} }
               //^
    """, tailText = " { ... }", typeText = "E")

    fun testEnumTupleVariant() = check(
        //language=Rust
        """
        enum E { X(i32, String) }
               //^
    """, tailText = "(i32, String)", typeText = "E")

    fun testField() = check(
        //language=Rust
        """
        struct S { field: String }
                   //^
    """, typeText = "String")

    fun testMod() {
        myFixture.configureByText("foo.rs", "")
        val lookup = (myFixture.file as RustFile).createLookupElement()
        val presentation = LookupElementPresentation()

        lookup.renderElement(presentation)
        assertThat(presentation.icon).isNotNull()
        assertThat(presentation.itemText).isEqualTo("foo")
    }

    private fun check(code: String, tailText: String? = null, typeText: String? = null) {
        val (element, @Suppress("UNUSED_VARIABLE") data) = configureAndFindElement(code)
        val lookup = (element.parentOfType<RustNamedElement>()!!).createLookupElement()
        val presentation = LookupElementPresentation()

        lookup.renderElement(presentation)
        assertThat(presentation.icon).isNotNull()
        assertThat(presentation.tailText).isEqualTo(tailText)
        assertThat(presentation.typeText).isEqualTo(typeText)
    }

}
