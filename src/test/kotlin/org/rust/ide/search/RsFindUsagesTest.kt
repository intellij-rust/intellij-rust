/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.startOffset

class RsFindUsagesTest : RsTestBase() {
    fun `test variable usages`() = doTestByText("""
        fn foo(x: i32) -> i32 {
             //^
            let y = x * 2;// - expr
            let x = x * 3 + y;// - expr
            x
        }
    """)

    fun `test pattern binding usages 1`() = doTestByText("""
        struct S{x:i32}
        fn foo() -> i32 {
            let S{x} = S{x:0};
                //^
            let y = x * 3;// - expr
            let x = y;
            x
        }
    """)

    fun `test pattern binding usages 2`() = doTestByText("""
        struct S{x:i32}
               //^
        fn foo() -> i32 {
            let y = S{x:0};// - init field
            let S{x} = y;// - variable binding
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
        impl B for A {}// - trait reference

        fn bar<T: B>() {}// - trait reference

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

    fun `test macro call`() = doTestByText("""
        macro_rules! foo {
                   //^
            () => { }
        }
        foo!();// - macro call
    """)

    fun `test struct defined by macro`() = doTestByText("""
        macro_rules! foo { ($($ i:item)*) => { $( $ i )* }; }
        foo! {
            struct X;
                 //^
            type T1 = X; // - type reference
        }
        type T2 = X; // - type reference
    """)

    fun `test method from trait`() = doTestByText("""
        struct B1; struct B2;
        trait A { fn foo(self, x: i32); }
                    //^
        impl A for B1 { fn foo(self, x: i32) {} }
        impl A for B2 { fn foo(self, x: i32) {} }
        fn foo(s: impl A) {
            B1.foo(); // - method call
            B2.foo(); // - method call
            s.foo();  // - method call
        }
    """)

    fun `test method from impl (use base declaration)`() {
        Messages.setTestDialog(TestDialog.OK)
        doTestByText("""
            struct B1;
            struct B2;
            trait A { fn foo(self, x: i32); }
            impl A for B1 { fn foo(self, x: i32) {} }
                              //^
            impl A for B2 { fn foo(self, x: i32) {} }
            fn foo(s: impl A) {
                B1.foo(); // - method call
                B2.foo(); // - method call
                s.foo();  // - method call
            }
        """)
    }

    fun `test method from impl (don't use base declaration)`() {
        Messages.setTestDialog(TestDialog.NO)
        doTestByText("""
            struct B1; struct B2;
            trait A { fn foo(self, x: i32); }
            impl A for B1 { fn foo(self, x: i32) {} }
                              //^
            impl A for B2 { fn foo(self, x: i32) {} }
            fn foo(s: impl A) {
                B1.foo(); // - method call
                B2.foo();
                s.foo();
            }
        """)
    }

    private fun doTestByText(@Language("Rust") code: String) {
        InlineFile(code)

        val (_, _, offset) = findElementWithDataAndOffsetInEditor<PsiElement>()
        val source = TargetElementUtil.getInstance().findTargetElement(
            myFixture.editor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED,
            offset
        ) as? RsNamedElement ?: error("Element not found")

        val actual = markersActual(source)
        val expected = markersFrom(code)
        assertEquals(expected.joinToString(COMPARE_SEPARATOR), actual.joinToString(COMPARE_SEPARATOR))
    }

    private fun markersActual(source: RsNamedElement) =
        myFixture.findUsages(source)
            .filter { it.element != null }
            .map { Pair(it.element?.line ?: -1, RsUsageTypeProvider.getUsageType(it.element).toString()) }
            .sortedBy { it.first }

    private fun markersFrom(text: String) =
        text.split('\n')
            .withIndex()
            .filter { it.value.contains(MARKER) }
            .map { Pair(it.index, it.value.substring(it.value.indexOf(MARKER) + MARKER.length).trim()) }

    private companion object {
        val MARKER = "// - "
        val COMPARE_SEPARATOR = " | "
    }

    val PsiElement.line: Int? get() = containingFile.viewProvider.document?.getLineNumber(startOffset)
}
