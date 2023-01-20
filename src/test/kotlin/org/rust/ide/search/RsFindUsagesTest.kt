/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.ui.TestDialog
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThrowableRunnable
import org.intellij.lang.annotations.Language
import org.rust.ExpandMacros
import org.rust.RsTestBase
import org.rust.ide.disableFindUsageTests
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.startOffset
import org.rust.openapiext.toPsiFile
import org.rust.withTestDialog

class RsFindUsagesTest : RsTestBase() {

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        if (!disableFindUsageTests) {
            super.runTestRunnable(testRunnable)
        }
    }

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

    fun `test lifetime defined in a macro call`() = doTestByText("""
        macro_rules! foo { ($($ i:item)*) => { $( $ i )* }; }
        foo! {
            fn foo<'a>() {
                  //^
                let a: &'a i32; // - null
            }
        }
    """)

    fun `test variable defined by a macro`() = doTestByText("""
        macro_rules! foo { ($($ t:tt)*) => { $($ t)* }; }
        fn main() {
            foo! {
                let a = 2;
            }     //^
            let _ = a; // - null
        }
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

    fun `test method from impl (use base declaration)`() = withTestDialog(TestDialog.OK) {
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

    fun `test method from impl (don't use base declaration)`() = withTestDialog(TestDialog.NO) {
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

    // https://github.com/intellij-rust/intellij-rust/issues/5265
    fun `test issue 5265`() = doTestByText("""
        mod foo {
            pub(crate) enum Foo { Bar { x: i32 } }
        }                       //^
        fn main() {
            let _ = foo::Foo::Bar { x: 123 }; // - init struct
        }
    """)

    fun `test usage in child mod`() = doTestByFileTree("""
    //- main.rs
        struct Foo;
             //^
        mod foo;
    //- foo.rs
        fn func(_: crate::Foo) {} // - type reference
    """)

    fun `test usage in child mod with path attribute`() = doTestByFileTree("""
    //- main.rs
        mod foo;
    //- foo/mod.rs
        struct Foo;
             //^
        #[path = "../bar.rs"]
        mod bar;
    //- bar.rs
        fn func(_: super::Foo) {} // - type reference
    """)

    @ExpandMacros
    fun `test usage in included file`() = doTestByFileTree("""
    //- main.rs
        mod foo;
    //- foo/mod.rs
        struct Foo;
             //^
        include!("../bar.rs");
    //- bar.rs
        fn func(_: Foo) {} // - type reference
    """)

    fun `test usage in child mod in unusual location 1`() = doTestByFileTree("""
    //- main.rs
        mod mod1;
    //- mod1/mod.rs
        mod mod2;
        fn func() {}
         //^
    //- mod1/mod2/mod.rs
        #[path = "../../foo.rs"]
        mod mod3;
    //- foo.rs
        fn main() {
            crate::mod1::func(); // - function call
        }
    """)

    private fun doTestByText(@Language("Rust") code: String) = doTestByFileTree("//- main.rs\n$code")

    private fun doTestByFileTree(@Language("Rust") code: String) {
        val testProject = configureByFileTree(code)

        val (_, _, offset) = findElementWithDataAndOffsetInEditor<PsiElement>()
        val source = TargetElementUtil.getInstance().findTargetElement(
            myFixture.editor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED,
            offset
        ) as? RsNamedElement ?: error("Element not found")

        val actualByFile = markersActual(source)
        for (filePath in testProject.files) {
            val file = myFixture.findFileInTempDir(filePath)!!.toPsiFile(project)!!
            val expected = markersFrom(file.text)
            val actual = actualByFile[file] ?: emptyList()
            assertEquals(
                "Mismatch in file $filePath",
                expected.joinToString(COMPARE_SEPARATOR),
                actual.joinToString(COMPARE_SEPARATOR)
            )
        }
    }

    private fun markersActual(source: RsNamedElement): Map<PsiFile, List<Pair<Int, String>>> =
        myFixture.findUsages(source)
            .mapNotNull { it.element }
            .groupBy(
                { it.containingFile },
                { element ->
                    val line = element.line ?: -1
                    val marker = RsUsageTypeProvider.getUsageType(element).toString()
                    line to marker
                }
            )
            .mapValues { (_, it) -> it.sortedBy { it.first } }

    private fun markersFrom(text: String) =
        text.split('\n')
            .withIndex()
            .filter { it.value.contains(MARKER) }
            .map { Pair(it.index, it.value.substring(it.value.indexOf(MARKER) + MARKER.length).trim()) }

    private companion object {
        const val MARKER: String = "// - "
        const val COMPARE_SEPARATOR: String = " | "
    }

    val PsiElement.line: Int? get() = containingFile.viewProvider.document?.getLineNumber(startOffset)
}
