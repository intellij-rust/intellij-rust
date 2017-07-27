/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.descendantsOfType

class RenameTest : RsTestBase() {
    fun `test function`() = doTest("spam", """
        mod a {
            mod b {
                fn /*caret*/foo() {}

                fn bar() {
                    foo()
                }
            }

            use self::b::foo;

            fn bar() {
                foo()
            }
        }

        fn foo() { }

        fn bar() {
            foo()
        }
    """, """
        mod a {
            mod b {
                fn spam() {}

                fn bar() {
                    spam()
                }
            }

            use self::b::spam;

            fn bar() {
                spam()
            }
        }

        fn foo() { }

        fn bar() {
            foo()
        }
    """)

    fun `test function with quote`() = doTest("'bar", """
        fn fo/*caret*/o() { foo(); }
    """, """
        fn bar() { bar(); }
    """)

    fun `test field`() = doTest("spam", """
        struct S { /*caret*/foo: i32 }

        fn main() {
            let x = S { foo: 92 };
            println!("{}", x.foo);
            let foo = 62;
            S { foo };
        }
    """, """
        struct S { spam: i32 }

        fn main() {
            let x = S { spam: 92 };
            println!("{}", x.spam);
            let foo = 62;
            S { spam: foo };
        }
    """)

    fun `test rename lifetime`() = doTest("'bar", """
        fn foo<'foo>(a: &/*caret*/'foo u32) {}
    """, """
        fn foo<'bar>(a: &'bar u32) {}
    """)

    fun `test rename lifetime without quote`() = doTest("baz", """
        fn foo<'foo>(a: &/*caret*/'foo u32) {}
    """, """
        fn foo<'baz>(a: &'baz u32) {}
    """)

    fun `test rename loop label`() = doTest("'bar", """
        fn foo() {
            /*caret*/'foo: loop { break 'foo }
        }
    """, """
        fn foo() {
            'bar: loop { break 'bar }
        }
    """)

    fun `test rename file`() = checkByDirectory("""
    //- main.rs
        use foo::Spam;
        mod foo;

        fn main() { let _ = Spam::Quux; }
    //- foo.rs
        pub enum Spam { Quux, Eggs }
    """, """
    //- main.rs
        use bar::Spam;
        mod bar;

        fn main() { let _ = Spam::Quux; }
    //- bar.rs
        pub enum Spam { Quux, Eggs }
    """) {
        val file = myFixture.configureFromTempProjectFile("foo.rs")
        myFixture.renameElement(file, "bar.rs")
    }

    fun `test rename mod declaration`() = checkByDirectory("""
    //- main.rs
        use foo::Spam;
        mod foo;

        fn main() { let _ = Spam::Quux; }
    //- foo.rs
        pub enum Spam { Quux, Eggs }
    """, """
    //- main.rs
        use bar::Spam;
        mod bar;

        fn main() { let _ = Spam::Quux; }
    //- bar.rs
        pub enum Spam { Quux, Eggs }
    """) {
        val mod = myFixture.configureFromTempProjectFile("main.rs").descendantsOfType<RsModDeclItem>().single()
        check(mod.name == "foo")
        val file = mod.reference.resolve()!!
        myFixture.renameElement(file, "bar")
    }

    fun `test rename file to mod_rs`() = checkByDirectory("""
    //- main.rs
        use foo::Spam;
        mod foo;

        fn main() { let _ = Spam::Quux; }
    //- foo.rs
        pub enum Spam { Quux, Eggs }
    """, """
    //- main.rs
        use foo::Spam;
        mod foo;

        fn main() { let _ = Spam::Quux; }
    //- mod.rs
        pub enum Spam { Quux, Eggs }
    """) {
        val file = myFixture.configureFromTempProjectFile("foo.rs")
        myFixture.renameElement(file, "mod.rs")
    }

    private fun doTest(
        newName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) {
        InlineFile(before).withCaret()
        myFixture.renameElementAtCaret(newName)
        myFixture.checkResult(after)
    }
}

