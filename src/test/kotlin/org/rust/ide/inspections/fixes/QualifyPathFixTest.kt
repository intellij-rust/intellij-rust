/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import org.rust.MockEdition
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsUnresolvedReferenceInspection

class QualifyPathFixTest : RsInspectionsTestBase(RsUnresolvedReferenceInspection::class) {
    fun `test function call`() = checkFixByText("Qualify path to `foo::bar`", """
        mod foo {
            pub fn bar() {}
        }
        fn main() {
            <error descr="Unresolved reference: `bar`">bar/*caret*/</error>();
        }
    """, """
        mod foo {
            pub fn bar() {}
        }
        fn main() {
            foo::bar();
        }
    """)

    fun `test struct type`() = checkFixByText("Qualify path to `foo::S`", """
        mod foo {
            pub struct S;
        }
        fn main() {
            let x: <error descr="Unresolved reference: `S`">S/*caret*/</error>;
        }
    """, """
        mod foo {
            pub struct S;
        }
        fn main() {
            let x: foo::S;
        }
    """)

    fun `test generic struct type`() = checkFixByText("Qualify path to `foo::S`", """
        mod foo {
            pub struct S<T>(T);
        }
        fn main() {
            let x: <error descr="Unresolved reference: `S`">S/*caret*/</error><u32>;
        }
    """, """
        mod foo {
            pub struct S<T>(T);
        }
        fn main() {
            let x: foo::S<u32>;
        }
    """)

    fun `test struct associated method call`() = checkFixByText("Qualify path to `foo::S`", """
        mod foo {
            pub struct S;
            impl S {
                pub fn bar() {}
            }
        }
        fn main() {
            let x = <error descr="Unresolved reference: `S`">S/*caret*/</error>::bar();
        }
    """, """
        mod foo {
            pub struct S;
            impl S {
                pub fn bar() {}
            }
        }
        fn main() {
            let x = foo::S::bar();
        }
    """)

    fun `test generic struct associated method call`() = checkFixByText("Qualify path to `foo::S`", """
        mod foo {
            pub struct S<T>(T);
            impl<T> S<T> {
                pub fn bar() {}
            }
        }
        fn main() {
            let x = <error descr="Unresolved reference: `S`">S/*caret*/</error>::<u32>::bar();
        }
    """, """
        mod foo {
            pub struct S<T>(T);
            impl<T> S<T> {
                pub fn bar() {}
            }
        }
        fn main() {
            let x = foo::S::<u32>::bar();
        }
    """)

    fun `test multiple candidates`() = checkFixIsUnavailable("Qualify path", """
        mod foo {
            pub fn bar() {}
        }
        mod baz {
            pub fn bar() {}
        }
        fn main() {
            <error descr="Unresolved reference: `bar`">bar/*caret*/</error>();
        }
    """)

    fun `test associated constant`() = checkFixIsUnavailable("Qualify path", """
        mod foo {
            pub trait Foo {
                const C: i32;
            }

            impl<T> Foo for T {
                const C: i32 = 0;
            }
        }

        fn main() {
            let x = i32::<error descr="Unresolved reference: `C`">C/*caret*/</error>(123);
        }
    """)

    fun `test trait impl`() = checkFixIsUnavailable("Qualify path", """
        mod foo {
            pub trait Foo {
                fn foo(&self) {}
            }

            impl<T> Foo for T {}
        }

        fn main() {
            let x = i32::<error descr="Unresolved reference: `foo`">foo/*caret*/</error>(123);
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2015)
    fun `test add extern crate with 2015 edition`() = checkFixByFileTree("Qualify path", """
        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Bar;
        }
        //- lib.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """, """
        //- lib.rs
        extern crate dep_lib_target;

        fn foo(t: dep_lib_target::foo::Bar) {}
    """)
}
