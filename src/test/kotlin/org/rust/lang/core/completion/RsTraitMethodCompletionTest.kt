/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.WithExcludedPath
import org.rust.ide.settings.RsCodeInsightSettings

class RsTraitMethodCompletionTest : RsCompletionTestBase() {

    fun `test auto import trait while method completion 1`() = doTest("""
        mod baz {
            pub trait Foo {
                fn foo(&self);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self) {}
            }
        }

        use crate::baz::Bar;

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

        use crate::baz::{Bar, Foo};

        fn main() {
            Bar.foo()/*caret*/
        }
    """)

    fun `test auto import trait while method completion 2`() = doTest("""
        mod baz {
            pub trait Foo {
                fn foo(&self, x: i32);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self, x: i32) {}
            }
        }

        use crate::baz::Bar;

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

        use crate::baz::{Bar, Foo};

        fn main() {
            Bar.foo(/*caret*/)
        }
    """)

    fun `test auto import trait while method completion 3`() = doTest("""
        mod baz {
            pub trait Foo {
                fn foo(&self);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self) {}
            }
        }

        use crate::baz::Bar;

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

        use crate::baz::{Bar, Foo};

        fn main() {
            Bar.foo()/*caret*/
        }
    """)

    fun `test auto import trait while method completion for multiple carets`() = doTest("""
        mod baz {
            pub trait Foo {
                fn foo(&self);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self) {}
            }
        }

        use crate::baz::Bar;

        fn main() {
            Bar.fo/*caret*/
            Bar.fo/*caret*/
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

        use crate::baz::{Bar, Foo};

        fn main() {
            Bar.foo()/*caret*/
            Bar.foo()/*caret*/
            Bar.foo()/*caret*/
        }
    """)

    fun `test do not insert trait import while method completion when trait in scope`() = doTest("""
        mod baz {
            pub trait Foo {
                fn foo(&self);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self) {}
            }
        }

        use crate::baz::{Bar, Foo};

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

        use crate::baz::{Bar, Foo};

        fn main() {
            Bar.foo()/*caret*/
        }
    """)

    fun `test do not insert trait import while method completion when setting disabled`() = doTest("""
        mod baz {
            pub trait Foo {
                fn foo(&self, x: i32);
            }

            pub struct Bar;

            impl Foo for Bar {
                fn foo(&self, x: i32) {}
            }
        }

        use crate::baz::Bar;

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

        use crate::baz::Bar;

        fn main() {
            Bar.foo(/*caret*/)
        }
    """, importOutOfScopeItems = false)

    @WithExcludedPath("crate::mod1::ExcludedTrait", onlyMethods = true)
    fun `test don't complete excluded trait method 1`() = checkNoCompletion("""
        mod mod1 {
            pub trait ExcludedTrait {
                fn method(&self) {}
            }
            impl<T> ExcludedTrait for T {}
        }
        fn main() {
            ().metho/*caret*/
        }
    """)

    @WithExcludedPath("crate::mod1::ExcludedTrait", onlyMethods = true)
    fun `test don't complete excluded trait method 2`() = checkNoCompletion("""
        mod mod1 {
            mod inner {
                pub trait ExcludedTrait {
                    fn method(&self) {}
                }
                impl<T> ExcludedTrait for T {}
            }
            pub use inner::*;
        }
        fn main() {
            ().metho/*caret*/
        }
    """)

    @WithExcludedPath("crate::mod1::ExcludedTrait", onlyMethods = true)
    fun `test complete excluded trait method with has different path`() = doTest("""
        mod mod1 {
            pub trait ExcludedTrait {
                fn method(&self) {}
            }
            impl<T> ExcludedTrait for T {}
        }
        mod mod2 {
            pub use crate::mod1::*;
        }
        fn main() {
            ().metho/*caret*/
        }
    """, """
        use crate::mod2::ExcludedTrait;

        mod mod1 {
            pub trait ExcludedTrait {
                fn method(&self) {}
            }
            impl<T> ExcludedTrait for T {}
        }
        mod mod2 {
            pub use crate::mod1::*;
        }
        fn main() {
            ().method()/*caret*/
        }
    """)

    @WithExcludedPath("crate::mod1::ExcludedTrait", onlyMethods = true)
    fun `test complete excluded trait method if trait already imported`() = doTest("""
        use crate::mod1::ExcludedTrait;
        mod mod1 {
            pub trait ExcludedTrait {
                fn method(&self) {}
            }
            impl<T> ExcludedTrait for T {}
        }
        fn main() {
            ().metho/*caret*/
        }
    """, """
        use crate::mod1::ExcludedTrait;
        mod mod1 {
            pub trait ExcludedTrait {
                fn method(&self) {}
            }
            impl<T> ExcludedTrait for T {}
        }
        fn main() {
            ().method()/*caret*/
        }
    """)

    private fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        importOutOfScopeItems: Boolean = true
    ) {
        val settings = RsCodeInsightSettings.getInstance()
        val initialValue = settings.importOutOfScopeItems
        settings.importOutOfScopeItems = importOutOfScopeItems
        try {
            doSingleCompletion(before, after)
        } finally {
            settings.importOutOfScopeItems = initialValue
        }
    }
}
