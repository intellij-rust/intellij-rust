/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.intellij.lang.annotations.Language
import org.rust.ExpandMacros
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.ext.*

@ExpandMacros
class RsMacroExpansionRangeMappingTest : RsTestBase() {
    fun `test struct name`() = checkOffset("""
        macro_rules! foo {
            ($ i:item) => { $ i };
        }
        foo! {
            struct /*caret*/Foo;
        }
        type T = Foo;
               //^
    """, SELECT_NAME)

    fun `test struct name from nested macro 1`() = checkOffset("""
        macro_rules! foo {
            ($ i:item) => { $ i };
        }
        foo! {
            foo! { pub struct /*caret*/Foo; }
        }
        type T = Foo;
               //^
    """, SELECT_NAME)

    fun `test struct name from nested macro 2`() = checkOffset("""
        macro_rules! foo {
            ($ i:item) => { $ i };
        }
        foo! {
            mod a {
                foo! { pub struct /*caret*/Foo; }
            }
        }
        type T = a::Foo;
                  //^
    """, SELECT_NAME)

    fun `test struct name from nested macro 3`() = checkOffset("""
        macro_rules! foo {
            ($ i:item) => { $ i };
        }
        macro_rules! bar {
            ($ i:item) => { mod a { $ i } };
        }
        bar! {
            foo! { pub struct /*caret*/Foo; }
        }
        type T = a::Foo;
                  //^
    """, SELECT_NAME)

    fun `test struct name passed via tt`() = checkOffset("""
        macro_rules! foo {
            ($($ i:tt)*) => { $($ i)* };
        }
        foo! {
            struct /*caret*/Foo;
        }
        type T = Foo;
               //^
    """, SELECT_NAME)

    fun `test struct`() = checkOffset("""
        macro_rules! foo {
            ($ i:item) => { $ i };
        }
        foo! {
            /*caret*/struct Foo;
        }
        type T = Foo;
               //^
    """)

    fun `test struct passed via tt`() = checkOffset("""
        macro_rules! foo {
            ($($ i:tt)*) => { $($ i)* };
        }
        foo! {
            /*caret*/struct Foo;
        }
        type T = Foo;
               //^
    """)

    fun `test struct field passed via tt`() = checkOffset("""
        macro_rules! foo {
            ($($ i:tt)*) => { $($ i)* };
        }
        foo! {
            struct Foo {
                /*caret*/bar: i32
            }
        }
        fn foo(foo: Foo) {
            foo.bar;
        }     //^
    """)

    fun `test expression`() = checkOffset("""
        macro_rules! foo {
            ($ e:expr) => { fn foo() { $ e } };
        }
        foo! {
            /*caret*/2 + 2
        }
        use self::foo;
                //^
    """) { it.descendantOfTypeStrict<RsBinaryExpr>()!! }

    fun `test not found when expanded from macro definition`() = checkNotFound("""
        macro_rules! foo {
            ($ i:item) => { $ i };
        }
        macro_rules! bar {
            () => { foo! { struct Foo; } };
        }
        bar!();
        type T = Foo;
               //^
    """)

    fun `test not found when expanded from nested macro definition`() = checkNotFound("""
        macro_rules! foo {
            () => { struct Foo; };
        }
        foo!();
        type T = Foo;
               //^
    """)

    fun `test struct name with docs in macro call 1`() = checkOffset("""
        macro_rules! foo {
            ($ i:item) => { $ i };
        }
        foo! {
            /// docs
            struct /*caret*/Foo;
        }
        type T = Foo;
               //^
    """, SELECT_NAME)

    fun `test struct name with docs in macro call 2`() = checkOffset("""
        macro_rules! foo {
            (#[$ m:meta] $ t:tt $ n:ident;) => { #[$ m] $ t $ n; };
        }
        foo! {
            /// docs
            struct /*caret*/Foo;
        }
        type T = Foo;
               //^
    """, SELECT_NAME)

    fun `test struct name with docs in macro call 3`() = checkOffset("""
        macro_rules! foo {
            ($($ i:item)*) => { $($ i)* };
        }
        foo! {
            /// docs
            fn foo() {}
            /// docs
            mod foo {
                /// docs
                pub struct /*caret*/Foo;
            }
        }
        type T = foo::Foo;
                    //^
    """, SELECT_NAME)

    private fun checkOffset(@Language("Rust") code: String, refiner: (RsElement) -> PsiElement = { it }) {
        InlineFile(code).withCaret()
        val ref = findElementInEditor<RsReferenceElement>("^")
        val resolved = ref.reference?.resolve() ?: error("Failed to resolve ${ref.text}")
        val elementInExpansion = refiner(resolved)
        check(elementInExpansion.isExpandedFromMacro) { "Must resolve to macro expansion" }
        val elementInCallBody = elementInExpansion.findElementExpandedFrom()
            ?: error("Failed to find element expanded from")
        assertEquals(myFixture.editor.caretModel.currentCaret.offset, elementInCallBody.startOffset)

        // do reverse translation of offsets and check that they are equal to original
        val recoveredElementsInExpansion = elementInCallBody.findExpansionElements()
            ?: error("recoveredElementsInExpansion must not be null")
        check(recoveredElementsInExpansion.size == 1) { "Expected list of 1 element, got: $recoveredElementsInExpansion" }
        val recoveredElementInExpansion = recoveredElementsInExpansion.single()
        assertEquals(elementInExpansion.startOffset, recoveredElementInExpansion.startOffset)
        check(recoveredElementInExpansion is LeafPsiElement)
    }

    private fun checkNotFound(@Language("Rust") code: String, refiner: (RsElement) -> PsiElement = { it }) {
        InlineFile(code)
        val ref = findElementInEditor<RsReferenceElement>("^")
        val resolved = ref.reference?.resolve() ?: error("Failed to resolve ${ref.text}")
        val elementInExpansion = refiner(resolved)
        check(elementInExpansion.isExpandedFromMacro) { "Must resolve to macro expansion" }
        val elementInCallBody = elementInExpansion.findElementExpandedFrom()
        assertNull(elementInCallBody)
    }

    companion object {
        private val SELECT_NAME = { e: RsElement -> (e as RsNameIdentifierOwner).nameIdentifier!! }
    }
}
