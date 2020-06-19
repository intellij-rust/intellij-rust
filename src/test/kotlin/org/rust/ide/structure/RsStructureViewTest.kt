/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.openapi.ui.Queryable
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PlatformTestUtil.expandAll
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.console.RsConsoleView
import javax.swing.JTree
import javax.swing.tree.TreePath

class RsStructureViewTest : RsTestBase() {
    fun `test functions`() = doTest("""
        pub(crate) fn fn_foo () {
            fn fn_bar () {
                fn fn_baz () {}
            }
        }

        #[test]
        fn test_something() { assert!(true); }

        pub fn double(x: i32) -> i32 { x * 2 }
    """, """
        -main.rs visibility=none
         -fn_foo() visibility=restricted
          -fn_bar() visibility=private
           fn_baz() visibility=private
         test_something() visibility=private
         double(i32) -> i32 visibility=public
    """)

    fun `test consts`() = doTest("""
        const SEVEN: f64 = 7.0;
        const BOOK_TITLE: &'static str = "Alice in Wonderland";
        pub const PUB_CONSTANT: i32 = 42;
        pub(crate) const PI: (i32, f64) = (3, 0.14159);
    """, """
        -main.rs visibility=none
         SEVEN: f64 visibility=private
         BOOK_TITLE: &'static str visibility=private
         PUB_CONSTANT: i32 visibility=public
         PI: (i32, f64) visibility=restricted
    """)

    fun `test enums`() = doTest("""
        #[derive(Clone, Copy, PartialEq)]
        pub enum CompileMode {
            Test,
            Build,
            Bench,
            Doc { deps: bool },
        }

        pub(crate) enum CompileFilter<'a> {
            Everything,
            Only {
                lib: bool,
                bins: &'a [String],
                examples: &'a [String],
                tests: &'a [String],
                benches: &'a [String],
            }
        }

        enum Message {
            Quit,
            ChangeColor(i32, i32, i32),
            Move { x: i32, y: i32 },
            Write(String),
        }
    """, """
        -main.rs visibility=none
         -CompileMode visibility=public
          Test visibility=none
          Build visibility=none
          Bench visibility=none
          -Doc visibility=none
           deps: bool visibility=none
         -CompileFilter visibility=restricted
          Everything visibility=none
          -Only visibility=none
           lib: bool visibility=none
           bins: &'a [String] visibility=none
           examples: &'a [String] visibility=none
           tests: &'a [String] visibility=none
           benches: &'a [String] visibility=none
         -Message visibility=private
          Quit visibility=none
          ChangeColor(i32, i32, i32) visibility=none
          -Move visibility=none
           x: i32 visibility=none
           y: i32 visibility=none
          Write(String) visibility=none
    """)

    fun `test struct impl`() = doTest("""
        struct Foo;
        impl<'a> Foo {
            pub const C1: i32 = 123;
            pub(crate) const C2: i32 = 123;
            const C3: i32 = 123;

            pub type T1 = i32;
            pub(crate) type T2 = i32;
            type T3 = i32;

            pub fn foo(name: &'a str) -> &'a str where 'a: 'static { name }
            pub(crate) fn inc(&mut self, num: f64) -> Option<f64> { Some(num + 1.0) }
            fn bar() {}
        }
    """, """
        -main.rs visibility=none
         Foo visibility=private
         -Foo visibility=none
          C1: i32 visibility=public
          C2: i32 visibility=restricted
          C3: i32 visibility=private
          T1 visibility=public
          T2 visibility=restricted
          T3 visibility=private
          foo(&'a str) -> &'a str visibility=public
          inc(f64) -> Option<f64> visibility=restricted
          bar() visibility=private
    """)

    fun `test trait impl`() = doTest("""
        /// Trait `Foo`
        pub trait Foo {
            const C: i32;
            type T;
            /// Method `query`
            fn query(&mut self, id: &u32) -> Option<&u32>;
        }

        impl Foo for &'static str {
            const C: i32 = 92;
            type T = i32;
            fn query(&mut self, id: &u32) -> Option<&u32> { None }
        }
    """, """
        -main.rs visibility=none
         -Foo visibility=public
          C: i32 visibility=none
          T visibility=none
          query(&u32) -> Option<&u32> visibility=none
         -Foo for &'static str visibility=none
          C: i32 visibility=none
          T visibility=none
          query(&u32) -> Option<&u32> visibility=none
    """)

    fun `test mods`() = doTest("""
        fn function() {}

        mod my {
            fn function() { }

            pub mod nested {
                pub fn function() { }
            }
        }

        pub(crate) mod mod2 {}

        mod moddecl;
        fn main() {
            function();
            my::nested::function();
        }
    """, """
        -main.rs visibility=none
         function() visibility=private
         -my visibility=private
          function() visibility=private
          -nested visibility=public
           function() visibility=public
         mod2 visibility=restricted
         moddecl visibility=private
         main() visibility=private
    """)

    fun `test statics`() = doTest("""
        static N: i32 = 5;
        static NAME: &'static str = "John Doe";
        pub static mut MUT_N: i64 = 5;
        pub(crate) static E: (i32, f64) = (2, 0.71828);
    """, """
        -main.rs visibility=none
         N: i32 visibility=private
         NAME: &'static str visibility=private
         MUT_N: i64 visibility=public
         E: (i32, f64) visibility=restricted
    """)

    fun `test extern`() = doTest("""
        extern {
            static N: i32;
            static NAME: &'static str;
            pub static mut MUT_N: i64;
            static E: (i32, f64);
            pub(crate) fn something(p1: i32) -> u8;
        }
    """, """
        -main.rs visibility=none
         N: i32 visibility=private
         NAME: &'static str visibility=private
         MUT_N: i64 visibility=public
         E: (i32, f64) visibility=private
         something(i32) -> u8 visibility=restricted
    """)

    fun `test macro`() = doTest("""
        macro_rules! makro {
            () => { };
        }
    """, """
        -main.rs visibility=none
         makro visibility=none
    """)

    fun `test structs`() = doTest("""
        /// Default implementation of `ExecEngine`.
        #[derive(Clone, Copy)]
        pub struct ProcessEngine;

        struct Numbers(f64, i8);

        /// Prototype for a command that must be executed.
        #[derive(Clone)]
        pub(crate) struct CommandPrototype {
            ty: CommandType,
            builder: ProcessBuilder,
        }
    """, """
        -main.rs visibility=none
         ProcessEngine visibility=public
         Numbers visibility=private
         -CommandPrototype visibility=restricted
          ty: CommandType visibility=private
          builder: ProcessBuilder visibility=private
    """)

    fun `test traits`() = doTest("""
        pub trait ExecEngine: Send + Sync {
            fn exec(&self, _: CommandPrototype) -> Result<(), ProcessError>;
            fn exec_with_output(&self, _: CommandPrototype) -> Result<Output, ProcessError>;
        }

        trait A {
            type B;
            const C: i32;
            const D: f64 = 92.92;
        }

        pub(crate) trait Registry {
            fn query(&mut self, name: &Dependency) -> CargoResult<Vec<Summary>>;
        }

        trait FnBox<A, R> {
            fn call_box(self: Box<Self>, a: A) -> R;
        }

        trait T { }
        trait P<X> { }
    """, """
        -main.rs visibility=none
         -ExecEngine visibility=public
          exec(CommandPrototype) -> Result<(), ProcessError> visibility=none
          exec_with_output(CommandPrototype) -> Result<Output, ProcessError> visibility=none
         -A visibility=private
          B visibility=none
          C: i32 visibility=none
          D: f64 visibility=none
         -Registry visibility=restricted
          query(&Dependency) -> CargoResult<Vec<Summary>> visibility=none
         -FnBox visibility=private
          call_box(A) -> R visibility=none
         T visibility=private
         P visibility=private
    """)

    fun `test type aliases`() = doTest("""
        type A = i32;
        pub type B = i32;
        pub(crate) type C = i32;
    """, """
        -main.rs visibility=none
         A visibility=private
         B visibility=public
         C visibility=restricted
    """)

    fun `test generic impl`() = doTest("""
        struct A<T> { }

        impl<T: Ord> A<T> {
            pub fn aaa() {}
        }
        impl<T: Display> A<T> {
            pub fn bbb() {}
        }
        impl<T: Display + Ord> A<T> {
            pub fn ccc() {}
        }
        impl<T> A<T> {
            pub fn ddd() {}
        }
        impl<T> A<T> where T: Ord {
            pub fn eee() {}
        }
        impl<T> A<T> where T: Display + Ord {
            pub fn fff() {}
        }
        impl<T> A<T> where T: Eq + {
            pub fn ggg() {}
        }
        impl<T> A<T> where {
            pub fn hhh() {}
        }
        impl<T> A<T> where T: {
            pub fn iii() {}
        }
        impl<T: Ord> A<T> where T: Display {
            pub fn jjj() {}
        }
        impl<T: Ord, F> A<F> where F: Into<T> {
            pub fn foo() {}
        }
    """, """
        -main.rs visibility=none
         A visibility=private
         -A<T> where T: Ord visibility=none
          aaa() visibility=public
         -A<T> where T: Display visibility=none
          bbb() visibility=public
         -A<T> where T: Display + Ord visibility=none
          ccc() visibility=public
         -A<T> visibility=none
          ddd() visibility=public
         -A<T> where T: Ord visibility=none
          eee() visibility=public
         -A<T> where T: Display + Ord visibility=none
          fff() visibility=public
         -A<T> where T: Eq visibility=none
          ggg() visibility=public
         -A<T> visibility=none
          hhh() visibility=public
         -A<T> visibility=none
          iii() visibility=public
         -A<T> where T: Ord + Display visibility=none
          jjj() visibility=public
         -A<F> where T: Ord, F: Into visibility=none
          foo() visibility=public
    """)

    fun `test generic trait impl`() = doTest("""
        struct Foo<T>(T);
        trait Bar<T> {}
        trait Baz {}

        impl<T> Foo<T> for Bar<T> {}
        impl<T: Baz> Foo<T> for Bar<T> {}
        impl<T: Clone, F> Foo<T> for Bar<F> where F: Ord {}
    """, """
        -main.rs visibility=none
         Foo visibility=private
         Bar visibility=private
         Baz visibility=private
         Foo<T> for Bar<T> visibility=none
         Foo<T> for Bar<T> where T: Baz visibility=none
         Foo<T> for Bar<F> where T: Clone, F: Ord visibility=none
    """)

    fun `test ?Sized in impl`() = doTest("""
        struct A<T> { }
        trait Bar<T> {}

        impl<T: ?Sized> A<T> {
            pub fn aaa() {}
        }
        impl<T> A<T> where T:Clone + ?Sized {
            pub fn aaa() {}
        }
        impl<T:?Sized> A<T> where T:Clone {
            pub fn aaa() {}
        }
        impl<T: ?Sized,F: ?Sized> Bar<F> for A<T> {}
    """, """
        -main.rs visibility=none
         A visibility=private
         Bar visibility=private
         -A<T> where T: ?Sized visibility=none
          aaa() visibility=public
         -A<T> where T: Clone + ?Sized visibility=none
          aaa() visibility=public
         -A<T> where T: ?Sized + Clone visibility=none
          aaa() visibility=public
         Bar<F> for A<T> where T: ?Sized, F: ?Sized visibility=none
    """)

    fun `test console variables basic`() = doTestForREPL("""
        let var1 = 1;
        let var2: u32 = 2;
        let var3 = "3";
    """, """
        -null visibility=none
         var1: i32 visibility=none
         var2: u32 visibility=none
         var3: &str visibility=none
    """)

    fun `test console variables destructuring tuples and arrays`() = doTestForREPL("""
        let (var1, (var2, var3)) = (1i32, (2u32, "3"));
        let [var4, var5] = [1, 2];
    """, """
        -null visibility=none
         var1: i32 visibility=none
         var2: u32 visibility=none
         var3: &str visibility=none
         var4: i32 visibility=none
         var5: i32 visibility=none
    """)

    fun `test console variables destructuring struct`() = doTestForREPL("""
        struct Struct1 { foo: u32 }
        struct Struct2 { field1: u16, field2: Struct1 }
        let struct2 = Struct2 { field1: 1, field2: Struct1 { foo: 0 } };
        let Struct2 { field1: var1, field2: Struct1 { foo } } = struct2;
    """, """
        -null visibility=none
         -Struct1 visibility=private
          foo: u32 visibility=private
         -Struct2 visibility=private
          field1: u16 visibility=private
          field2: Struct1 visibility=private
         struct2: Struct2 visibility=none
         var1: u16 visibility=none
         foo: u32 visibility=none
    """)

    fun `test console variables destructuring enum`() = doTestForREPL("""
        enum E { Variant(u8) }
        let var1 @ E::Variant(..) = E::Variant(1);
    """, """
        -null visibility=none
         -E visibility=private
          Variant(u8) visibility=none
         var1: E visibility=none
    """)

    private fun doTest(@Language("Rust") code: String, expected: String, fileName: String = "main.rs") {
        val normExpected = expected.trimIndent()
        myFixture.configureByText(fileName, code)
        myFixture.testStructureView {
            expandAll(it.tree)
            assertTreeEqual(it.tree, normExpected)
        }
    }

    private fun doTestForREPL(code: String, expected: String) = doTest(code, expected, RsConsoleView.VIRTUAL_FILE_NAME)

    private fun assertTreeEqual(tree: JTree, expected: String) {
        val printInfo = Queryable.PrintInfo(
            arrayOf(RsStructureViewElement.NAME_KEY),
            arrayOf(RsStructureViewElement.VISIBILITY_KEY)
        )
        val treeStringPresentation = PlatformTestUtil.print(tree, TreePath(tree.model.root), printInfo, false)
        assertEquals(expected.trim(), treeStringPresentation.trim())
    }
}
