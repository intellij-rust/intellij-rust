/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.import

import org.intellij.lang.annotations.Language
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

    fun `test import enum variant`() = doAvailableTest("""
        mod foo {
            pub enum Foo { A }
        }

        fn main() {
            Foo::A/*caret*/;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub enum Foo { A }
        }

        fn main() {
            Foo::A/*caret*/;
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

    fun `test import function method`() = doAvailableTest("""
        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn foo() {}
            }
        }

        fn main() {
            Foo::foo/*caret*/();
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn foo() {}
            }
        }

        fn main() {
            Foo::foo/*caret*/();
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

    fun `test import module declared via module declaration`() = doAvailableTestWithFileTree("""
        //- foo/bar.rs
        fn foo_bar() {}
        //- main.rs
        mod foo {
            pub mod bar;
        }
        fn main() {
            bar::foo_bar/*caret*/();
        }
    """, """
        use foo::bar;

        mod foo {
            pub mod bar;
        }
        fn main() {
            bar::foo_bar/*caret*/();
        }
    """)

    fun `test filter import candidates 1`() = doAvailableTest("""
        mod foo1 {
            pub fn bar() {}
        }

        mod foo2 {
            pub mod bar {
                pub fn foo_bar() {}
            }
        }

        fn main() {
            bar/*caret*/();
        }
    """, """
        use foo1::bar;

        mod foo1 {
            pub fn bar() {}
        }

        mod foo2 {
            pub mod bar {
                pub fn foo_bar() {}
            }
        }

        fn main() {
            bar/*caret*/();
        }
    """)

    fun `test filter import candidates 2`() = doAvailableTest("""
        mod foo1 {
            pub fn bar() {}
        }

        mod foo2 {
            pub mod bar {
                pub fn foo_bar() {}
            }
        }

        fn main() {
            bar::foo_bar/*caret*/();
        }
    """, """
        use foo2::bar;

        mod foo1 {
            pub fn bar() {}
        }

        mod foo2 {
            pub mod bar {
                pub fn foo_bar() {}
            }
        }

        fn main() {
            bar::foo_bar/*caret*/();
        }
    """)

    fun `test filter members without owner prefix`() = doUnavailableTest("""
        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn foo() {}
            }
        }

        fn main() {
            foo/*caret*/();
        }
    """)

    fun `test don't try to import item if it can't be resolved`() = doUnavailableTest("""
        mod foo {
            pub mod bar {
            }
        }
        fn main() {
            bar::foo_bar/*caret*/();
        }
    """)

    fun `test don't import trait method`() = doUnavailableTest("""
        mod foo {
            pub trait Bar {
                fn bar();
            }
        }
        fn main() {
            Bar::bar/*caret*/();
        }
    """)

    fun `test don't import trait const`() = doUnavailableTest("""
        mod foo {
            pub trait Bar {
                const BAR: i32;
            }
        }
        fn main() {
            Bar::BAR/*caret*/();
        }
    """)

    fun `test multiple import`() = doAvailableTestWithMultipleChoice("""
        mod foo {
            pub struct Foo;
            pub mod bar {
                pub struct Foo;
            }
        }

        mod baz {
            pub struct Foo;
            mod qwe {
                pub struct Foo;
            }
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """, setOf("foo::Foo", "foo::bar::Foo", "baz::Foo"), "foo::bar::Foo", """
        use foo::bar::Foo;

        mod foo {
            pub struct Foo;
            pub mod bar {
                pub struct Foo;
            }
        }

        mod baz {
            pub struct Foo;
            mod qwe {
                pub struct Foo;
            }
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    private fun doAvailableTestWithMultipleChoice(@Language("Rust") before: String,
                                                  expectedElements: Set<String>,
                                                  choice: String,
                                                  @Language("Rust") after: String) {
        withMockImportItemUi(object : ImportItemUi {
            override fun chooseItem(items: List<ImportCandidate>, callback: (ImportCandidate) -> Unit) {
                val actualItems = items.mapTo(HashSet()) { it.info.usePath }
                assertEquals(expectedElements, actualItems)
                val selectedValue = items.find { it.info.usePath == choice }
                    ?: error("Can't find `$choice` in `$actualItems`")
                callback(selectedValue)
            }
        }) { doAvailableTest(before, after) }
    }
}
