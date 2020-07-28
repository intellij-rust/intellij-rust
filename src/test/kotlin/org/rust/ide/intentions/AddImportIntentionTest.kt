/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class AddImportIntentionTest : RsIntentionTestBase(AddImportIntention()) {
    fun `test not available in use statements`() = doUnavailableTest("""
        mod foo {
            pub struct Foo;
        }
        use foo::/*caret*/Foo;
    """)

    fun `test not available for unresolved paths`() = doUnavailableTest("""
        fn main() {
            foo::/*caret*/Foo;
        }
    """)

    fun `test not available for leaf paths 1`() = doUnavailableTest("""
        mod foo {
            pub struct Foo;
        }
        use foo::Foo;

        fn main() {
            /*caret*/Foo;
        }
    """)

    fun `test not available for leaf paths 2`() = doUnavailableTest("""
        mod foo {
            pub struct Foo;
        }

        fn main() {
            /*caret*/foo::Foo;
        }
    """)

    fun `test basic import`() = doAvailableTest("""
        mod foo {
            pub struct Foo;
        }

        fn main() {
            foo::/*caret*/Foo;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo;
        }

        fn main() {
            /*caret*/Foo;
        }
    """)

    fun `test keep type arguments`() = doAvailableTest("""
        mod foo {
            pub struct Foo<'a, T>(pub &'a T);
        }

        fn main() {
            foo::/*caret*/Foo::<'_, u32>(&1);
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo<'a, T>(pub &'a T);
        }

        fn main() {
            /*caret*/Foo::<'_, u32>(&1);
        }
    """)

    fun `test keep nested type`() = doAvailableTest("""
        mod foo {
            pub struct Foo<T>(pub T);
        }

        fn main() {
            foo::/*caret*/Foo::<foo::Foo<u32>>;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo<T>(pub T);
        }

        fn main() {
            /*caret*/Foo::<Foo<u32>>;
        }
    """)

    fun `test nested path 1`() = doAvailableTest("""
        mod a {
            pub mod b {
                pub mod c {
                    pub struct Foo;
                }
            }
        }

        fn main() {
            a::b::c::/*caret*/Foo;
        }
    """, """
        use a::b::c::Foo;

        mod a {
            pub mod b {
                pub mod c {
                    pub struct Foo;
                }
            }
        }

        fn main() {
            /*caret*/Foo;
        }
    """)

    fun `test nested path 2`() = doAvailableTest("""
        mod a {
            pub mod b {
                pub mod c {
                    pub struct Foo;
                }
            }
        }

        fn main() {
            a::b::/*caret*/c::Foo;
        }
    """, """
        use a::b::c;

        mod a {
            pub mod b {
                pub mod c {
                    pub struct Foo;
                }
            }
        }

        fn main() {
            /*caret*/c::Foo;
        }
    """)

    fun `test partial import`() = doAvailableTest("""
        use a::b;

        mod a {
            pub mod b {
                pub mod c {
                    pub struct Foo;
                }
            }
        }

        fn main() {
            b::c::/*caret*/Foo;
        }
    """, """
        use a::b;
        use b::c::Foo;

        mod a {
            pub mod b {
                pub mod c {
                    pub struct Foo;
                }
            }
        }

        fn main() {
            /*caret*/Foo;
        }
    """)

    fun `test function call without an owner`() = doAvailableTest("""
        mod foo {
            pub fn new() {}
        }

        fn main() {
            foo::/*caret*/new();
        }
    """, """
        use foo::new;

        mod foo {
            pub fn new() {}
        }

        fn main() {
            /*caret*/new();
        }
    """)

    fun `test function call with an owner`() = doAvailableTest("""
        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn new() -> Self { Foo }
            }
        }

        fn main() {
            foo::Foo::/*caret*/new();
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn new() -> Self { Foo }
            }
        }

        fn main() {
            Foo::/*caret*/new();
        }
    """)

    fun `test mod`() = doAvailableTest("""
        mod a {
            pub mod b {
                pub mod c {

                }
            }
        }

        fn main() {
            a::b::/*caret*/c;
        }
    """, """
        use a::b;

        mod a {
            pub mod b {
                pub mod c {

                }
            }
        }

        fn main() {
            b::/*caret*/c;
        }
    """)

    fun `test constant`() = doAvailableTest("""
        mod a {
            pub const CONST: u32 = 0;
        }

        fn main() {
            a::/*caret*/CONST;
        }
    """, """
        use a::CONST;

        mod a {
            pub const CONST: u32 = 0;
        }

        fn main() {
            /*caret*/CONST;
        }
    """)

    fun `test trait`() = doAvailableTest("""
        mod a {
            pub trait Trait {}
        }

        fn main() {
            let _: &a::/*caret*/Trait;
        }
    """, """
        use a::Trait;

        mod a {
            pub trait Trait {}
        }

        fn main() {
            let _: &/*caret*/Trait;
        }
    """)

    fun `test type alias`() = doAvailableTest("""
        mod a {
            pub type Type = ();
        }

        fn main() {
            let _: a::/*caret*/Type;
        }
    """, """
        use a::Type;

        mod a {
            pub type Type = ();
        }

        fn main() {
            let _: /*caret*/Type;
        }
    """)

    fun `test enum`() = doAvailableTest("""
        mod a {
            pub enum Enum { V1 }
        }

        fn main() {
            let _: a::/*caret*/Enum;
        }
    """, """
        use a::Enum;

        mod a {
            pub enum Enum { V1 }
        }

        fn main() {
            let _: /*caret*/Enum;
        }
    """)

    fun `test enum variant`() = doAvailableTest("""
        mod a {
            pub enum Enum { V1 }
        }

        fn main() {
            a::Enum::/*caret*/V1;
        }
    """, """
        use a::Enum::V1;

        mod a {
            pub enum Enum { V1 }
        }

        fn main() {
            /*caret*/V1;
        }
    """)

    fun `test don't import if same name exists in scope`() = doUnavailableTest("""
        mod foo {
            pub struct Foo;
        }

        struct Foo;

        fn main() {
            foo::/*caret*/Foo;
        }
    """)

    fun `test shorten path if import already exists`() = doAvailableTest("""
        use foo::Foo;

        mod foo {
            pub struct Foo;
        }

        fn main() {
            foo::/*caret*/Foo;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo;
        }

        fn main() {
            /*caret*/Foo;
        }
    """)

    fun `test import if same name exists in a different namespace`() = doAvailableTest("""
        mod foo {
            pub const Foo: u32 = 0;
        }

        struct Foo {}

        fn main() {
            foo::/*caret*/Foo;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub const Foo: u32 = 0;
        }

        struct Foo {}

        fn main() {
            /*caret*/Foo;
        }
    """)

    fun `test replace usage`() = doAvailableTest("""
        mod foo {
            pub struct Foo;
        }

        fn main() {
            foo::/*caret*/Foo;
            foo::Foo;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo;
        }

        fn main() {
            /*caret*/Foo;
            Foo;
        }
    """)

    fun `test replace usage inside module`() = doAvailableTest("""
        mod foo {
            pub mod bar {
                pub struct Bar;
            }

            fn f1() {
                bar::/*caret*/Bar;
            }
            fn f2() {
                bar::Bar;
            }
        }

        fn f3() {
            foo::bar::Bar;
        }
    """, """
        mod foo {
            use bar::Bar;

            pub mod bar {
                pub struct Bar;
            }

            fn f1() {
                /*caret*/Bar;
            }
            fn f2() {
                Bar;
            }
        }

        fn f3() {
            foo::bar::Bar;
        }
    """)

    fun `test replace usage different path 1`() = doAvailableTest("""
        use foo::bar;
        use foo::bar::baz;

        mod foo {
            pub mod bar {
                pub mod baz {
                    pub struct S;
                }
            }
        }

        fn main() {
            foo::bar::baz::/*caret*/S;
            bar::baz::S;
            baz::S;
        }
    """, """
        use foo::bar;
        use foo::bar::baz;
        use foo::bar::baz::S;

        mod foo {
            pub mod bar {
                pub mod baz {
                    pub struct S;
                }
            }
        }

        fn main() {
            S;
            S;
            S;
        }
    """)

    fun `test replace usage different path 2`() = doAvailableTest("""
        use foo::bar;
        use foo::bar::baz;

        mod foo {
            pub mod bar {
                pub mod baz {
                    pub struct S;
                }
            }
        }

        fn main() {
            foo::bar::/*caret*/baz::S;
            bar::baz::S;
            baz::S;
        }
    """, """
        use foo::bar;
        use foo::bar::baz;

        mod foo {
            pub mod bar {
                pub mod baz {
                    pub struct S;
                }
            }
        }

        fn main() {
            /*caret*/baz::S;
            baz::S;
            baz::S;
        }
    """)
}
