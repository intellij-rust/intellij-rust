/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTraitImplementationInspection

class AddSelfToFunctionDeclarationTest : RsInspectionsTestBase(RsTraitImplementationInspection::class) {

    fun `test add self reference to function declaration`() = checkFixByText("Add self to function", """
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
            fn foo(&self);
        }

        impl T for S {
            fn foo(/*caret*/&self) {
            }
        }
    """)

    fun `test add self reference to trait function declaration`() = checkFixByText("Add self to trait", """
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
            fn foo(&self);
        }

        impl T for S {
            fn foo(&self/*caret*/) {
            }
        }
    """)

    fun `test add self to trait function declaration`() = checkFixByText("Add self to trait", """
        struct S;

        trait T {
            fn foo();
        }

        impl T for S {
            fn foo(/*error*/self/*caret*//*error**/) {
            }
        }
    """, """
        struct S;

        trait T {
            fn foo(self);
        }

        impl T for S {
            fn foo(self/*caret*/) {
            }
        }
    """)

    fun `test add self mut reference to trait function declaration`() = checkFixByText("Add self to trait", """
        struct S;

        trait T {
            fn foo();
        }

        impl T for S {
            fn foo(/*error*/&mut self/*caret*//*error**/) {
            }
        }
    """, """
        struct S;

        trait T {
            fn foo(&mut self);
        }

        impl T for S {
            fn foo(&mut self/*caret*/) {
            }
        }
    """)

    fun `test add self reference like type to trait function declaration`() = checkFixByText("Add self to trait", """
        struct S;
        trait T {
            fn foo();
        }
        impl T for S {
            fn foo(/*error*/self: &mut Arc<Self>/*caret*//*error**/) {
            }
        }
    """, """
        struct S;
        trait T {
            fn foo(self: &mut Arc<Self>);
        }
        impl T for S {
            fn foo(self: &mut Arc<Self>/*caret*/) {
            }
        }
    """)

    fun `test add deeply nested self to trait function declaration`() = checkFixByText("Add self to trait", """
        struct S;
        trait T {
            fn foo();
        }
        impl T for S {
            fn foo(/*error*/self: Pin<Arc<Rc<Box<Self>>>>/*caret*//*error**/) {
            }
        }
    """, """
        struct S;
        trait T {
            fn foo(self: Pin<Arc<Rc<Box<Self>>>>);
        }
        impl T for S {
            fn foo(self: Pin<Arc<Rc<Box<Self>>>>/*caret*/) {
            }
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test add self not available if trait is in another crate`() = checkFixIsUnavailableByFileTree("Add self to trait", """
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
