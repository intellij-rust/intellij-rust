/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTraitImplementationInspection

class RemoveSelfFixTest : RsInspectionsTestBase(RsTraitImplementationInspection::class) {

    fun `test remove self from trait function declaration`() = checkFixByText("Remove self from trait", """
        struct S;

        trait T {
            fn foo(&self);
        }

        impl T for S {
            fn foo/*error*/(/*caret*/)/*error**/ {
            }
        }
    """, """
        struct S;

        trait T {
            fn foo();
        }

        impl T for S {
            fn foo(/*caret*/) {
            }
        }
    """)

    fun `test remove self from function declaration`() = checkFixByText("Remove self from function", """
        struct S;

        trait T {
            fn foo();
        }

        impl T for S {
            fn foo(/*error*/&self/*caret*//*error**/) {
            }
        }
    """, """
        struct S;

        trait T {
            fn foo();
        }

        impl T for S {
            fn foo(/*caret*/) {
            }
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test remove self not available if trait is in another crate`() = checkFixIsUnavailableByFileTree("Remove self from trait", """
        //- dep-trait/lib.rs
        pub trait T {
            fn foo();
        }

        //- main.rs
        extern crate dep_trait;
        use dep_trait::T;

        struct S;
        impl T for S {
            fn foo(self: Pin<Arc<Rc<Box<Self>>>>/*caret*/) {
            }
        }
    """)
}
