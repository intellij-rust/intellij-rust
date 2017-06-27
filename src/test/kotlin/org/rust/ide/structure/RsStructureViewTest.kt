/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.testFramework.PlatformTestUtil.assertTreeEqual
import com.intellij.util.ui.tree.TreeUtil
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

class RsStructureViewTest : RsTestBase() {
    fun `test functions`() = doTest("""
        fn fn_foo () {}

        #[test]
        fn test_something() { assert!(true); }

        fn double(x: i32) -> i32 { x * 2 }
    """, """
        -main.rs
         fn_foo()
         test_something()
         double(i32) -> i32
    """)

    fun `test consts`() = doTest("""
        const SEVEN: f64 = 7.0;
        const BOOK_TITLE: &'static str = "Alice in Wonderland";
        pub const PUB_CONSTANT: i32 = 42;
        const PI: (i32, f64) = (3, 0.14159);
    """, """
        -main.rs
         SEVEN: f64
         BOOK_TITLE: &'static str
         PUB_CONSTANT: i32
         PI: (i32, f64)
    """)

    fun `test enums`() = doTest("""
        #[derive(Clone, Copy, PartialEq)]
        pub enum CompileMode {
            Test,
            Build,
            Bench,
            Doc { deps: bool },
        }

        pub enum CompileFilter<'a> {
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
        -main.rs
         -CompileMode
          Test
          Build
          Bench
          Doc
         -CompileFilter
          Everything
          Only
         -Message
          Quit
          ChangeColor(i32, i32, i32)
          Move
          Write(String)
    """)

    fun `test struct impl`() = doTest("""
        struct Foo;
        impl<'a> Foo {
            pub fn foo(name: &'a str) -> &'a str where 'a: 'static { name }
            pub fn inc(&mut self, num: f64) -> Option<f64> { Some(num + 1.0) }
        }
    """, """
        -main.rs
         Foo
         -Foo
          foo(&'a str) -> &'a str
          inc(f64) -> Option<f64>
    """)

    fun `test trait impl`() = doTest("""
        /// Trait `Foo`
        pub trait Foo {
            const C: i32;
            /// Method `query`
            fn query(&mut self, id: &u32) -> Option<&u32>;
        }

        impl Foo for &'static str {
            const C: i32 = 92;
            fn query(&mut self, id: &u32) -> Option<&u32> { None }
        }
    """, """
        -main.rs
         -Foo
          C: i32
          query(&u32) -> Option<&u32>
         -Foo for &'static str
          C: i32
          query(&u32) -> Option<&u32>
    """)

    fun `test mods`() = doTest("""
        fn function() {}

        mod my {
            fn function() { }

            pub mod nested {
                pub fn function() { }
            }
        }

        mod moddecl;
        fn main() {
            function();
            my::nested::function();
        }
    """, """
        -main.rs
         function()
         -my
          function()
          -nested
           function()
         moddecl
         main()
    """)

    fun `test statics`() = doTest("""
        static N: i32 = 5;
        static NAME: &'static str = "John Doe";
        pub static mut MUT_N: i64 = 5;
        static E: (i32, f64) = (2, 0.71828);
    """, """
        -main.rs
         N: i32
         NAME: &'static str
         MUT_N: i64
         E: (i32, f64)
    """)

    fun `test structs`() = doTest("""
        /// Default implementation of `ExecEngine`.
        #[derive(Clone, Copy)]
        pub struct ProcessEngine;

        pub struct Numbers(f64, i8);

        /// Prototype for a command that must be executed.
        #[derive(Clone)]
        pub struct CommandPrototype {
            ty: CommandType,
            builder: ProcessBuilder,
        }
    """, """
        -main.rs
         ProcessEngine
         Numbers
         -CommandPrototype
          ty: CommandType
          builder: ProcessBuilder
    """)

    fun `test traits`() = doTest("""
        pub trait ExecEngine: Send + Sync {
            fn exec(&self, CommandPrototype) -> Result<(), ProcessError>;
            fn exec_with_output(&self, CommandPrototype) -> Result<Output, ProcessError>;
        }

        trait A {
            type B;
            const C: i32;
            const D: f64 = 92.92;
        }

        pub trait Registry {
            fn query(&mut self, name: &Dependency) -> CargoResult<Vec<Summary>>;
        }

        trait FnBox<A, R> {
            fn call_box(self: Box<Self>, a: A) -> R;
        }

        trait T { }
        trait P<X> { }
    """, """
        -main.rs
         -ExecEngine: Send + Sync
          exec(CommandPrototype) -> Result<(), ProcessError>
          exec_with_output(CommandPrototype) -> Result<Output, ProcessError>
         -A
          C: i32
          D: f64
         -Registry
          query(&Dependency) -> CargoResult<Vec<Summary>>
         -FnBox<A, R>
          call_box(A) -> R
         T
         P<X>
    """)

    fun `test type aliases`() = doTest("""
        type A = i32;
    """, """
        -main.rs
         A
    """)

    private fun doTest(@Language("Rust") code: String, expected: String) {
        val normExpected = expected.trimIndent() + "\n"
        myFixture.configureByText("main.rs", code)
        myFixture.testStructureView {
            TreeUtil.expandAll(it.tree)
            assertTreeEqual(it.tree, normExpected)
        }
    }
}
