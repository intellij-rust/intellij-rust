/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.import

import org.rust.ide.intentions.RsIntentionTestBase

class ImportNameIntentionTest : RsIntentionTestBase(ImportNameIntention()) {

    fun `test import struct`() = doAvailableTest("""
        mod foo {
            pub struct Foo;
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo;
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test import function`() = doAvailableTest("""
        mod foo {
            pub fn bar() -> i32 { unimplemented!() }
        }

        fn main() {
            let f = bar/*caret*/();
        }
    """, """
        use foo::bar;

        mod foo {
            pub fn bar() -> i32 { unimplemented!() }
        }

        fn main() {
            let f = bar/*caret*/();
        }
    """)

    fun `test import module`() = doAvailableTest("""
        mod foo {
            pub mod bar {
                pub fn foo_bar() -> i32 { unimplemented!() }
            }
        }

        fn main() {
            let f = bar/*caret*/::foo_bar();
        }
    """, """
        use foo::bar;

        mod foo {
            pub mod bar {
                pub fn foo_bar() -> i32 { unimplemented!() }
            }
        }

        fn main() {
            let f = bar/*caret*/::foo_bar();
        }
    """)

    fun `test insert use item after existing use items`() = doAvailableTest("""
        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        use foo::Bar;

        fn main() {
            let f = Foo/*caret*/;
        }
    """, """
        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        use foo::Bar;
        use foo::Foo;

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test import item from nested module`() = doAvailableTest("""
        mod foo {
            pub mod bar {
                pub struct Foo;
            }
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """, """
        use foo::bar::Foo;

        mod foo {
            pub mod bar {
                pub struct Foo;
            }
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test don't try to import private item`() = doUnavailableTest("""
        mod foo {
            struct Foo;
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test don't try to import from private mod`() = doUnavailableTest("""
        mod foo {
            mod bar {
                pub struct Foo;
            }
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test complex module structure`() = doAvailableTest("""
        mod aaa {
            mod bbb {
                fn foo() {
                    let x = Foo/*caret*/;
                }
            }
        }
        mod ccc {
            pub mod ddd {
                pub struct Foo;
            }
            mod eee {
                struct Foo;
            }
        }
    """, """
        mod aaa {
            mod bbb {
                use ccc::ddd::Foo;

                fn foo() {
                    let x = Foo/*caret*/;
                }
            }
        }
        mod ccc {
            pub mod ddd {
                pub struct Foo;
            }
            mod eee {
                struct Foo;
            }
        }
    """)

    fun `test complex module structure with file modules`() = doAvailableTestWithFileTree("""
        //- aaa/mod.rs
        mod bbb;
        //- aaa/bbb/mod.rs
        fn foo() {
            let x = Foo/*caret*/;
        }
        //- ccc/mod.rs
        pub mod ddd;
        mod eee;
        //- ccc/ddd/mod.rs
        pub struct Foo;
        //- ccc/eee/mod.rs
        struct Foo;
        //- main.rs
        mod aaa;
        mod ccc;
    """, """
        use ccc::ddd::Foo;

        fn foo() {
            let x = Foo/*caret*/;
        }
    """)
}
