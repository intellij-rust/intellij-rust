/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.ext.*

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
        macro_rules! gen_foo {
            ($ e:expr) => { fn foo() { $ e; } };
        }
        gen_foo! {
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

    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test macro body is fully mapped attr_as_is`() = checkFullyMapped("""
        #[test_proc_macros::attr_as_is]
        /*<selection>*/fn foo() {
            let a: ((f64,), f64) = {((1.,), 2.2)};
        }/*</selection>*/
    """)

    /** A test for [org.rust.lang.core.macros.tt.SubtreeIdRecovery] */
    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test macro body is fully mapped attr_as_is_discard_punct_spans`() = checkFullyMapped("""
        #[test_proc_macros::attr_as_is_discard_punct_spans]
        /*<selection>*/fn foo() {
            let a: ((f64,), f64) = {((1.,), 2.2)};
        }/*</selection>*/
    """)

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

    private fun checkFullyMapped(@Language("Rust") code: String) {
        val preparedCode = code.trimIndent()
            .replace("/*<selection>*/", "<selection><caret>")
            .replace("/*</selection>*/", "</selection>")
        InlineFile(preparedCode)
        val macroCall = myFixture.file
            .descendantsOfType<RsAttrProcMacroOwner>()
            .mapNotNull { it.procMacroAttribute.attr }
            .single()
        val expansion = macroCall.expansionResult.unwrap()
        val ranges = expansion.ranges.ranges
        if (ranges.size != 1) {
            fail("Expected `1` mappable range, found `${ranges.size}` ranges")
        }

        val mappedSourceRange = ranges.first().srcRange.shiftRight(macroCall.bodyTextRange!!.startOffset)
        myFixture.editor.selectionModel.setSelection(mappedSourceRange.startOffset, mappedSourceRange.endOffset)
        myFixture.checkResult(preparedCode)
    }

    companion object {
        private val SELECT_NAME = { e: RsElement -> (e as RsNameIdentifierOwner).nameIdentifier!! }
    }
}
