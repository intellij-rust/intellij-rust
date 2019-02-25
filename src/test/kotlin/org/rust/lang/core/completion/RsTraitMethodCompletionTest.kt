/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsTraitMethodCompletionTest : RsCompletionTestBase() {

    fun `test auto import trait while method completion 1`() = doSingleCompletion("""
        mod baz {
            pub trait Foo {
                fn foo(&self);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self) {}
            }
        }

        use baz::Bar;

        fn main() {
            Bar.fo/*caret*/
        }
    """, """
        mod baz {
            pub trait Foo {
                fn foo(&self);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self) {}
            }
        }

        use baz::{Bar, Foo};

        fn main() {
            Bar.foo()/*caret*/
        }
    """)

    fun `test auto import trait while method completion 2`() = doSingleCompletion("""
        mod baz {
            pub trait Foo {
                fn foo(&self, x: i32);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self, x: i32) {}
            }
        }

        use baz::Bar;

        fn main() {
            Bar.fo/*caret*/()
        }
    """, """
        mod baz {
            pub trait Foo {
                fn foo(&self, x: i32);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self, x: i32) {}
            }
        }

        use baz::{Bar, Foo};

        fn main() {
            Bar.foo(/*caret*/)
        }
    """)

    fun `test auto import trait while method completion 3`() = doSingleCompletion("""
        mod baz {
            pub trait Foo {
                fn foo(&self);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self) {}
            }
        }

        use baz::Bar;

        fn main() {
            Bar./*caret*/
        }
    """, """
        mod baz {
            pub trait Foo {
                fn foo(&self);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self) {}
            }
        }

        use baz::{Bar, Foo};

        fn main() {
            Bar.foo()/*caret*/
        }
    """)

    fun `test do not insert trait import while method completion when trait in scope`() = doSingleCompletion("""
        mod baz {
            pub trait Foo {
                fn foo(&self);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self) {}
            }
        }

        use baz::{Bar, Foo};

        fn main() {
            Bar.fo/*caret*/
        }
    """, """
        mod baz {
            pub trait Foo {
                fn foo(&self);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self) {}
            }
        }

        use baz::{Bar, Foo};

        fn main() {
            Bar.foo()/*caret*/
        }
    """)
}
