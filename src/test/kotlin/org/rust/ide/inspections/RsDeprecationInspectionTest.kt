/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

/**
 * Tests for Deprecated Attribute inspection.
 */
class RsDeprecationInspectionTest : RsInspectionsTestBase(RsDeprecationInspection()) {

    fun `test deprecated function without params`() = checkByText("""
        #[deprecated()]
        pub fn foo() {
        }

        fn main() {
            <warning descr="`foo` is deprecated">foo</warning>();
        }
    """)

    fun `test deprecated function with since param only`() = checkByText("""
        #[deprecated(since="1.0.0")]
        pub fn foo() {
        }

        fn main() {
            <warning descr="`foo` is deprecated since 1.0.0">foo</warning>();
        }
    """)

    fun `test deprecated function with note param only`() = checkByText("""
        #[deprecated(note="here could be your reason")]
        pub fn foo() {
        }

        fn main() {
            <warning descr="`foo` is deprecated: here could be your reason">foo</warning>();
        }
    """)

    fun `test deprecated function with since and note params`() = checkByText("""
        #[deprecated(since="1.0.0", note="here could be your reason")]
        pub fn foo() {
        }

        fn main() {
            <warning descr="`foo` is deprecated since 1.0.0: here could be your reason">foo</warning>();
        }
    """)

    fun `test rustc_deprecated attribute`() = checkByText("""
        #[rustc_deprecated(since="1.0.0", reason="here could be your reason")]
        pub fn foo() {
        }

        fn main() {
            <warning descr="`foo` is deprecated since 1.0.0: here could be your reason">foo</warning>();
        }
    """)

    fun `test deprecated struct`() = checkByText("""
        #[deprecated]
        struct Foo;

        fn main() {
            let foo = <warning descr="`Foo` is deprecated">Foo</warning>{};
        }
    """)

    fun `test deprecated struct method`() = checkByText("""
        struct Foo;

        impl Foo {
            #[deprecated]
            fn new() -> Foo {
                Foo { }
            }
        }

        fn main() {
            let foo = Foo::<warning descr="`new` is deprecated">new</warning>();
        }
    """)

    fun `test deprecated trait`() = checkByText("""
        #[deprecated]
        trait Bar {}

        struct Foo;

        impl <warning descr="`Bar` is deprecated">Bar</warning> for Foo {
        }
    """)

    fun `test deprecated trait method`() = checkByText("""
        trait Bar {
            #[deprecated]
            fn foo(&self);
        }

        struct Foo;

        impl Bar for Foo {
            fn foo(&self) {
                println("foo")
            }
        }

        fn main() {
            let foo = Foo;
            foo.<warning descr="`foo` is deprecated">foo</warning>();
        }
    """)

    fun `test deprecated enum`() = checkByText("""
        #[deprecated]
        enum Numbers {
            One,
            Two,
            Three
        }

        fn main() {
            <warning descr="`Numbers` is deprecated">Numbers</warning>::One;
        }
    """)

    fun `test deprecated enum item`() = checkByText("""
        enum Numbers {
            #[deprecated]
            One,
            Two,
            Three
        }

        fn main() {
            Numbers::<warning descr="`One` is deprecated">One</warning>;
        }
    """)

    fun `test deprecated type alias`() = checkByText("""
        #[deprecated]
        type Name = String;

        fn main() {
            let name: <warning descr="`Name` is deprecated">Name</warning> = "test".to_string();
        }
    """)

    fun `test deprecated variable`() = checkByText("""
        #[deprecated]
        static SOME_INT: i32 = 5;

        fn main() {
            let a = <warning descr="`SOME_INT` is deprecated">SOME_INT</warning>;
        }
    """)

    fun `test deprecated inline module`() = checkByText("""
        #[deprecated]
        mod foo {
            pub static SOME_INT: i32 = 5;
        }

        fn main() {
            let a = <warning descr="`foo` is deprecated">foo</warning>::SOME_INT;
        }
    """)

    fun `test deprecated variable in struct`() = checkByText("""
        struct Foo {
            #[deprecated]
            pub x: i32
        }

        fn main() {
            let a = Foo { <warning descr="`x` is deprecated">x</warning>: 123 };
        }
    """)

    fun `test deprecated non-inline module`() = checkByFileTree("""
        //- main.rs
            #[deprecated]
            mod foo;

            fn main() {
                let a = /*caret*/<warning descr="`foo` is deprecated">foo</warning>::SOME_INT;
            }
        //- foo.rs
            pub static SOME_INT: i32 = 5;
    """)

    fun `test allow deprecated function`() = checkByText("""
        #[deprecated]
        pub fn foo() {
        }

        #[allow(deprecated)]
        fn main() {
            foo();
        }
    """)

    fun `test allow deprecated statement`() = checkByText("""
        #[deprecated]
        pub fn foo() {
        }

        fn main() {
            #[allow(deprecated)]
            foo();
        }
    """)

    fun `test allow deprecated item in module`() = checkByText("""
        #[allow(deprecated)]
        mod foo {
            #[deprecated]
            static SOME_INT: i32 = 5;
            static OTHER_INT: i32 = SOME_INT;
        }
    """)

    fun `test allow deprecated inner attribute`() = checkByText("""
        #![allow(deprecated)]

        #[deprecated]
        fn foo() {
            println!("TEST")
        }

        fn main() {
            foo();
        }
    """)

    fun `test AST is not loaded when deprecated item in another file`() = checkByFileTree("""
    //- main.rs
        mod foo;

        fn main() {/*caret*/
            foo::<warning descr="`bar` is deprecated since 1.0.0: here could be your reason">bar</warning>();
        }
    //- foo.rs
        #[deprecated(since="1.0.0", note="here could be your reason")]
        pub fn bar() {}
    """)

    fun `test suppression quick fix for statement 1`() = expect<AssertionError> {
        checkFixByText("Suppress `deprecated` for statement", """
            #[deprecated]
            pub fn foo() {}

            fn main() {
                <warning descr="`foo` is deprecated">foo/*caret*/</warning>();
            }
        """, """
            #[deprecated]
            pub fn foo() {}

            fn main() {
                #[allow(deprecated)]
                foo/*caret*/();
            }
        """)
    }

    fun `test suppression quick fix for statement 2`() = checkFixByText("Suppress `deprecated` for statement", """
        #[deprecated]
        pub struct Foo;

        fn main() {
            let foo = <warning descr="`Foo` is deprecated">Foo/*caret*/</warning>;
        }
    """, """
        #[deprecated]
        pub struct Foo;

        fn main() {
            #[allow(deprecated)] let foo = Foo/*caret*/;
        }
    """)

    // TODO: fix field attribute formatting
    fun `test suppression quick fix for field`() = checkFixByText("Suppress `deprecated` for field x", """
        #[deprecated]
        pub struct Foo;

        pub struct Bar {
            x: <warning descr="`Foo` is deprecated">/*caret*/Foo</warning>
        }
    """, """
        #[deprecated]
        pub struct Foo;

        pub struct Bar {
            #[allow(deprecated)]x: /*caret*/Foo
        }
    """)

    fun `test suppression quick fix for struct`() = checkFixByText("Suppress `deprecated` for struct Bar", """
        #[deprecated]
        pub struct Foo;

        pub struct Bar {
            x: <warning descr="`Foo` is deprecated">Foo/*caret*/</warning>
        }
    """, """
        #[deprecated]
        pub struct Foo;

        #[allow(deprecated)]
        pub struct Bar {
            x: Foo/*caret*/
        }
    """)

    fun `test suppression quick fix for fn`() = checkFixByText("Suppress `deprecated` for fn main", """
        #[deprecated]
        pub fn foo() {}

        fn main() {
            <warning descr="`foo` is deprecated">foo/*caret*/</warning>();
        }
    """, """
        #[deprecated]
        pub fn foo() {}

        #[allow(deprecated)]
        fn main() {
            foo/*caret*/();
        }
    """)

    fun `test suppression quick fix for file`() = checkFixByText("Suppress `deprecated` for file main.rs", """
        #[deprecated]
        pub fn foo() {}

        fn main() {
            <warning descr="`foo` is deprecated">foo/*caret*/</warning>();
        }
    """, """
        #![allow(deprecated)]

        #[deprecated]
        pub fn foo() {}

        fn main() {
            foo/*caret*/();
        }
    """)

}
