/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement

class RsFindUsagesTest : RsTestBase() {
    fun `test variable usages`() = doTestByText("""
        fn foo(x: i32) -> i32 {
             //^
            let y = x * 2;// - expr
            let x = x * 3 + y;// - expr
            x
        }
    """)

    fun `test function usages`() = doTestByText("""
         fn foo() {}
           //^

         fn bar() { foo() } // - function call

         mod a {
             use super::foo;// - use

             fn baz() { foo() } // - function call
         }

         mod b {
             fn foo() {}
             fn bar() { foo() }
         }
    """)

    fun `test mod usages`() = doTestByText("""
        mod b {
          //^
            fn bar() { foo() }
        }

        mod a {
            use super::b;// - use
        }
    """)

    fun `test struct usages`() = doTestByText("""
        struct B;
             //^

        impl B {// - impl
            fn new() -> B {// - type reference
                B// - null
            }
        }

        fn test() {
            let b = B::new();// - function call
        }
        mod a {
            use super::B;// - use
        }
    """)

    fun `test struct with fields usages`() = doTestByText("""
        struct B {
             //^
            test: u32
        }
        trait A {}

        impl B {// - impl
            fn new() -> B {// - type reference
                B {// - init struct
                    test: 0
                }
            }
        }
        impl A for B {}// - impl

        fn bar<T: A>() {}

        fn test() {
            let b = B::new();// - function call
            bar::<B>();// - type reference
        }
        mod a {
            use super::B;// - use
        }
    """)

    fun `test trait usages`() = doTestByText("""
        trait B {}
            //^
        struct A;
        impl B for A {}// - trait ref

        fn bar<T: B>() {}// - trait ref

        mod a {
            use super::B;// - use
        }
    """)

    fun `test struct field usages`() = doTestByText("""
        struct B {
            test: u32
            //^
        }

        impl B {
            fn new() -> B {
                B {
                    test: 0// - init field
                }
            }
        }

        fn test() {
            let mut b = B::new();
            println!("{}", b.test);// - field
            b.test = 10;// - field
        }
    """)

    fun `test variable 2 usages`() = doTestByText("""
        struct B;

        impl B {
            fn new() -> B {
                B
            }
            fn foo(&self) {}
        }
        fn bar(b: B) {

        }

        fn test() {
            let b = B::new();
              //^
            b.foo();// - dot expr
            println!("{:?}", b);// - macro argument
            bar(b);// - argument
        }
    """)

    private fun doTestByText(@Language("Rust") code: String) {
        InlineFile(code)
        val source = findElementInEditor<RsNamedElement>()

        val actual = markersActual(source)
        val expected = markersFrom(code)
        assertEquals(expected.joinToString(COMPARE_SEPARATOR), actual.joinToString(COMPARE_SEPARATOR))
    }

    private fun markersActual(source: RsNamedElement) =
        myFixture.findUsages(source)
            .filter { it.element != null }
            .map { Pair(it.element?.line ?: -1, RsUsageTypeProvider.getUsageType(it.element).toString()) }

    private fun markersFrom(text: String) =
        text.split('\n')
            .withIndex()
            .filter { it.value.contains(MARKER) }
            .map { Pair(it.index, it.value.substring(it.value.indexOf(MARKER) + MARKER.length).trim()) }

    private companion object {
        val MARKER = "// - "
        val COMPARE_SEPARATOR = " | "
    }

    val PsiElement.line: Int? get() = containingFile.viewProvider.document?.getLineNumber(textRange.startOffset)
}
