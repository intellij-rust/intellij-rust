/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.MockEdition
import org.rust.UseNewResolve
import org.rust.WithEnabledInspections
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.inspections.lints.RsUnusedImportInspection

@UseNewResolve
@WithEnabledInspections(RsUnusedImportInspection::class)
class AddImportAliasIntentionTest : RsIntentionTestBase(AddImportAliasIntention::class) {
    fun `test not available with use group`() = doUnavailableTest("""
        use foo::/*caret*/{Foo};
    """)

    fun `test not available with alias`() = doUnavailableTest("""
        use foo::/*caret*/Foo as Bar;
    """)

    fun `test not available for star import`() = doUnavailableTest("""
        use foo::*/*caret*/;
    """)

    fun `test not available for crate`() = doUnavailableTest("""
        use crate/*caret*/;
    """)

    fun `test not available for super`() = doUnavailableTest("""
        use crate::{super/*caret*/};
    """)

    fun `test not available for self`() = doUnavailableTest("""
        use crate::{self/*caret*/};
    """)

    fun `test simple`() = doAvailableTestWithLiveTemplate("""
        use foo::/*caret*/Foo;
    """, "T\t", """
        use foo::Foo as T/*caret*/;
    """)

    fun `test nested`() = doAvailableTestWithLiveTemplate("""
        use foo::{Foo, Bar/*caret*/};
    """, "T\t", """
        use foo::{Foo, Bar as T/*caret*/};
    """)

    fun `test qualified`() = doAvailableTestWithLiveTemplate("""
        use foo::{Foo, bar::Bar/*caret*/};
    """, "T\t", """
        use foo::{Foo, bar::Bar as T/*caret*/};
    """)

    fun `test replace type usage`() = doAvailableTestWithLiveTemplate("""
        mod foo {
            pub struct S;
        }
        mod bar {
            use crate::foo::S/*caret*/;

            fn baz(_: S) {}
        }
    """, "T\t", """
        mod foo {
            pub struct S;
        }
        mod bar {
            use crate::foo::S as T/*caret*/;

            fn baz(_: T) {}
        }
    """)

    fun `test replace expression usage`() = doAvailableTestWithLiveTemplate("""
        mod foo {
            pub struct S<T>(T);

            impl <T> S<T> {
                pub fn new() -> Self { todo!() }
            }
        }
        mod bar {
            use crate::foo::S/*caret*/;

            fn baz() {
                let _ = S::<u32>::new();
            }
        }
    """, "T\t", """
        mod foo {
            pub struct S<T>(T);

            impl <T> S<T> {
                pub fn new() -> Self { todo!() }
            }
        }
        mod bar {
            use crate::foo::S as T/*caret*/;

            fn baz() {
                let _ = T::<u32>::new();
            }
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test replace usage in use speck`() = doAvailableTestWithLiveTemplate("""
        mod foo {
            pub mod bar {
                pub struct S;
            }
        }
        mod baz {
            use crate::foo::bar/*caret*/;
            use bar::S;

            fn fun(_: S) {}
        }
    """, "T\t", """
        mod foo {
            pub mod bar {
                pub struct S;
            }
        }
        mod baz {
            use crate::foo::bar as T/*caret*/;
            use T::S;

            fn fun(_: S) {}
        }
    """)

    fun `test trait import`() = doAvailableTestWithLiveTemplate("""
        mod foo {
            pub trait Trait {
                fn foo(&self);
            }
            impl Trait for () {
                fn foo(&self) {}
            }
        }
        mod bar {
            use crate::foo::Trait/*caret*/;

            fn baz() {
                ().foo();
            }
        }
    """, "T\t", """
        mod foo {
            pub trait Trait {
                fn foo(&self);
            }
            impl Trait for () {
                fn foo(&self) {}
            }
        }
        mod bar {
            use crate::foo::Trait as T/*caret*/;

            fn baz() {
                ().foo();
            }
        }
    """)
}
