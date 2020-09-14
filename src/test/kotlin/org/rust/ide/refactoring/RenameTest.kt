/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.refactoring.BaseRefactoringProcessor
import org.intellij.lang.annotations.Language
import org.rust.EmptyDescriptor
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.openapiext.toPsiDirectory

class RenameTest : RsTestBase() {
    fun `test function`() = doTest("spam", """
        mod a {
            pub mod b {
                pub fn /*caret*/foo() {}

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
            pub mod b {
                pub fn spam() {}

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
            let S { foo } = x;
            let foo = 62;
            S { foo };
        }
    """, """
        struct S { spam: i32 }

        fn main() {
            let x = S { spam: 92 };
            println!("{}", x.spam);
            let S { spam: foo } = x;
            let foo = 62;
            S { spam: foo };
        }
    """)

    fun `test pat binding in let`() = doTest("spam", """
        struct S { foo: i32 }
        fn main() {
            let S { ref foo } = S { foo: 92 };
            let x = foo/*caret*/;
        }
    """, """
        struct S { foo: i32 }
        fn main() {
            let S { foo: ref spam } = S { foo: 92 };
            let x = spam;
        }
    """)

    fun `test pat binding in fn`() = doTest("spam", """
        struct S { foo: i32 }
        fn test(S { foo }:S) {
            let x = foo/*caret*/;
        }
    """, """
        struct S { foo: i32 }
        fn test(S { foo: spam }:S) {
            let x = spam;
        }
    """)

    fun `test field initialization shorthand`() = doTest("spam", """
        struct S { foo: i32 }

        fn main() {
            let /*caret*/foo = 92;
            let x = S { foo };
            println!("{}", x.foo);
        }
    """, """
        struct S { foo: i32 }

        fn main() {
            let spam = 92;
            let x = S { foo: spam };
            println!("{}", x.foo);
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
        use r#mod::Spam;
        mod r#mod;

        fn main() { let _ = Spam::Quux; }
    //- mod.rs
        pub enum Spam { Quux, Eggs }
    """) {
        val file = myFixture.configureFromTempProjectFile("foo.rs")
        myFixture.renameElement(file, "mod.rs")
    }

    fun `test rename mod declaration for dir module`() = checkByDirectory("""
    //- main.rs
        use foo::Spam;
        mod foo;

        fn main() { let _ = Spam::Quux; }
    //- foo/mod.rs
        pub enum Spam { Quux, Eggs }
    """, """
    //- main.rs
        use bar::Spam;
        mod bar;

        fn main() { let _ = Spam::Quux; }
    //- bar/mod.rs
        pub enum Spam { Quux, Eggs }
    """) {
        val mod = myFixture.configureFromTempProjectFile("main.rs").descendantsOfType<RsModDeclItem>().single()
        check(mod.name == "foo")
        val file = mod.reference.resolve()!!
        myFixture.renameElement(file, "bar")
    }

    fun `test rename dir for dir module`() = checkByDirectory("""
    //- main.rs
        use foo::Spam;
        mod foo;

        fn main() { let _ = Spam::Quux; }
    //- foo/mod.rs
        pub enum Spam { Quux, Eggs }
    """, """
    //- main.rs
        use bar::Spam;
        mod bar;

        fn main() { let _ = Spam::Quux; }
    //- bar/mod.rs
        pub enum Spam { Quux, Eggs }
    """) {
        val mod = myFixture.configureFromTempProjectFile("main.rs").descendantsOfType<RsModDeclItem>().single()
        check(mod.name == "foo")
        val file = myFixture.configureFromTempProjectFile("foo/mod.rs")
        myFixture.renameElement(file, "bar")
    }

    @ProjectDescriptor(EmptyDescriptor::class)
    fun `test do not invoke rename refactoring for directory outside of cargo project`() {
        val dir = myFixture.tempDirFixture.findOrCreateDir("dir").toPsiDirectory(project)!!
        RsDirectoryRenameProcessor.Testmarks.rustDirRenameHandler.checkNotHit {
            myFixture.renameElement(dir, "dir2")
        }
    }

    fun `test rename file to keyword`() = checkByDirectory("""
    //- main.rs
        mod foo;
        use foo::bar;

        fn main() {
            bar();
        }
    //- foo.rs
        pub fn bar() { println!("Bar"); }
    """, """
    //- main.rs
        mod r#match;
        use r#match::bar;

        fn main() {
            bar();
        }
    //- match.rs
        pub fn bar() { println!("Bar"); }
    """) {
        val file = myFixture.configureFromTempProjectFile("foo.rs")
        myFixture.renameElement(file, "match")
    }

    fun `test does not rename lambda parameter shadowed in an outer comment`() = doTest("new_name", """
        fn test() {
            let param = 123;
            vec!["abc"].iter().inspect(|param/*caret*/| {
                println!("{}", param);
                // Prints out `param`.
            });
            // `param` printed out.
        }
    """, """
        fn test() {
            let param = 123;
            vec!["abc"].iter().inspect(|new_name| {
                println!("{}", new_name);
                // Prints out `new_name`.
            });
            // `param` printed out.
        }
    """)

    fun `test rename raw identifier 1`() = doTest("bar", """
        fn foo() {}
        fn main() {
            r#foo/*caret*/();
        }
    """, """
        fn bar() {}
        fn main() {
            bar();
        }
    """)

    fun `test rename raw identifier 2`() = doTest("match", """
        fn foo() {}
        fn main() {
            foo/*caret*/();
        }
    """, """
        fn r#match() {}
        fn main() {
            r#match();
        }
    """)

    fun `test handle file references in include macro`() = checkByDirectory("""
        //- main.rs
            include!("foo.rs");
        //- foo.rs
            fn foo() {}
    """, """
        //- main.rs
            include!("bar.rs");
        //- bar.rs
            fn foo() {}
    """) {
        val file = myFixture.configureFromTempProjectFile("foo.rs")
        myFixture.renameElement(file, "bar.rs")
    }

    fun `test handle file references in path attribute`() = checkByDirectory("""
        //- main.rs
            #[path = "foo.rs"]
            mod baz;
        //- foo.rs
            fn foo() {}
    """, """
        //- main.rs
            #[path = "bar.rs"]
            mod baz;
        //- bar.rs
            fn foo() {}
    """) {
        val file = myFixture.configureFromTempProjectFile("foo.rs")
        myFixture.renameElement(file, "bar.rs")
    }

    fun `test base function`() = doTest("spam", """
        struct S;
        trait T {
            fn foo/*caret*/();
        }
        impl T for S {
            fn foo() {}
        }
    """, """
        struct S;
        trait T {
            fn spam();
        }
        impl T for S {
            fn spam() {}
        }
    """)

    fun `test base const`() = doTest("SPAM", """
        struct S;
        trait T {
            const A/*caret*/: i32;
        }
        impl T for S {
            const A: i32 = 42;
        }
    """, """
        struct S;
        trait T {
            const SPAM: i32;
        }
        impl T for S {
            const SPAM: i32 = 42;
        }
    """)

    fun `test base type alias`() = doTest("SPAM", """
        struct S;
        trait T {
            type A/*caret*/;
        }
        impl T for S {
            type A = ();
        }
    """, """
        struct S;
        trait T {
            type SPAM;
        }
        impl T for S {
            type SPAM = ();
        }
    """)

    fun `test function implementation`() = doTest("spam", """
        struct S1;
        struct S2;
        trait T {
            fn foo();
        }
        impl T for S {
            fn foo/*caret*/() {}
        }
        impl T for S {
            fn foo() {}
        }
    """, """
        struct S1;
        struct S2;
        trait T {
            fn spam();
        }
        impl T for S {
            fn spam() {}
        }
        impl T for S {
            fn spam() {}
        }
    """)

    fun `test const implementation`() = doTest("SPAM", """
        struct S1;
        struct S2;
        trait T {
            const A: i32;
        }
        impl T for S1 {
            const A/*caret*/: i32 = 42;
        }
        impl T for S2 {
            const A: i32 = 42;
        }
    """, """
        struct S1;
        struct S2;
        trait T {
            const SPAM: i32;
        }
        impl T for S1 {
            const SPAM: i32 = 42;
        }
        impl T for S2 {
            const SPAM: i32 = 42;
        }
    """)

    fun `test type alias implementation`() = doTest("SPAM", """
        struct S1;
        struct S2;
        trait T {
            type A;
        }
        impl T for S1 {
            type A/*caret*/ = ();
        }
        impl T for S2 {
            type A = ();
        }
    """, """
        struct S1;
        struct S2;
        trait T {
            type SPAM;
        }
        impl T for S1 {
            type SPAM = ();
        }
        impl T for S2 {
            type SPAM = ();
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/3483
    fun `test change file type`() = checkByDirectory("""
    //- foo.txt
        fn foo() {}
    """, """
    //- foo.rs
        fn foo() {}
    """) {
        val file = myFixture.configureFromTempProjectFile("foo.txt")
        myFixture.renameElement(file, "foo.rs")
    }

    fun `test rename reference inside a macro call`() = doTest("Spam", """
        macro_rules! foo { ($ i:item) => { $ i }; }
        struct Foo;
        foo! { type T = /*caret*/Foo; }
    """, """
        macro_rules! foo { ($ i:item) => { $ i }; }
        struct Spam;
        foo! { type T = Spam; }
    """)

    fun `test use initialization shorthand after rename`() = doTest("value", """
        struct Foo {
            value: u32
        }
        fn bar(/*caret*/foo: u32) -> Foo {
            Foo {
                value: foo
            }
        }
    """, """
        struct Foo {
            value: u32
        }
        fn bar(value: u32) -> Foo {
            Foo {
                value
            }
        }
    """)

    fun `test use initialization shorthand after rename with comment`() = doTest("value", """
        struct Foo {
            value: u32
        }
        fn bar(/*caret*/foo: u32) -> Foo {
            Foo {
                value: /* comment */ foo
            }
        }
    """, """
        struct Foo {
            value: u32
        }
        fn bar(value: u32) -> Foo {
            Foo {
                value /* comment */
            }
        }
    """)

    fun `test can't use initialization shorthand after rename`() = doTest("bar", """
        struct Foo {
            value: u32
        }
        fn bar(/*caret*/foo: u32) -> Foo {
            Foo {
                value: foo
            }
        }
    """, """
        struct Foo {
            value: u32
        }
        fn bar(bar: u32) -> Foo {
            Foo {
                value: bar
            }
        }
    """)

    fun `test can't use initialization shorthand after rename 2`() = doTest("value", """
        struct Foo {
            value: u32,
        }

        fn bar(/*caret*/bar: u32) -> Foo {
            Foo {
                value: foo(bar),
            }
        }
        fn foo(value: u32) -> u32 { unimplemented!() }
    """, """
        struct Foo {
            value: u32,
        }

        fn bar(value: u32) -> Foo {
            Foo {
                value: foo(value),
            }
        }
        fn foo(value: u32) -> u32 { unimplemented!() }
    """)

    fun `test rename variable with variable conflict`() = doTestWithConflicts("a", """
        fn test() {
            let a = 1;
            let b/*caret*/ = 1;
        }
    """, setOf("Variable `a` is already declared in function `test`"))

    fun `test rename variable with parameter conflict`() = doTestWithConflicts("a", """
        fn test(a: u32) {
            let b/*caret*/ = 1;
        }
    """, setOf("Parameter `a` is already declared in function `test`"))

    fun `test rename variable with binding conflict`() = doTestWithConflicts("a", """
        struct S { a: i32 }
        fn test(S { a }: S) {
            let b/*caret*/ = 1;
        }
    """, setOf("Binding `a` is already declared in function `test`"))

    fun `test rename variable multiple conflicts`() = doTestWithConflicts("a", """
        struct S { a: i32 }
        fn test(S { a }: S) {
            let a = 1;
            let b/*caret*/ = 1;
        }
    """, setOf("Binding `a` is already declared in function `test`", "Variable `a` is already declared in function `test`"))

    fun `test rename parameter with variable conflict`() = doTestWithConflicts("a", """
        fn test(b/*caret*/: u32) {
            let a = 1;
            let c = 1;
        }
    """, setOf("Variable `a` is already declared in function `test`"))

    fun `test rename variable nested function`() = doTest("a", """
        fn test() {
            let a = 1;
            fn foo() {
                let b/*caret*/ = 1;
            }
        }
    """, """
        fn test() {
            let a = 1;
            fn foo() {
                let a = 1;
            }
        }
    """)

    private fun doTest(
        newName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) {
        InlineFile(before).withCaret()
        val element = myFixture.elementAtCaret
        myFixture.renameElement(element, newName, true, true)
        myFixture.checkResult(after)
    }

    private fun doTestWithConflicts(
        newName: String,
        @Language("Rust") code: String,
        conflicts: Set<String>
    ) {
        try {
            doTest(newName, code, code)
            error("Conflicts $conflicts missing")
        }
        catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
            assertEquals(e.messages.toSet(), conflicts)
        }
    }
}
