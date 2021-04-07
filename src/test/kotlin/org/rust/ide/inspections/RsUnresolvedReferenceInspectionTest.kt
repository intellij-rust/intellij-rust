/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.intellij.lang.annotations.Language
import org.rust.MockAdditionalCfgOptions
import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor
import org.rust.ide.inspections.import.AutoImportFix

class RsUnresolvedReferenceInspectionTest : RsInspectionsTestBase(RsUnresolvedReferenceInspection::class) {

    fun `test unresolved reference with quick fix`() = checkByText("""
        mod foo {
            pub struct Foo;
        }

        fn main() {
            let x = <error descr="Unresolved reference: `Foo`">Foo</error>;
        }
    """)

    fun `test unresolved references without quick fix 1`() = checkByText("""
        fn main() {
            let x = Foo;
        }
    """, true)

    fun `test unresolved references without quick fix 2`() = checkByText("""
        fn main() {
            let x = <error descr="Unresolved reference: `Foo`">Foo</error>;
        }
    """, false)

    fun `test reference with multiple resolve`() = checkByText("""
        #[cfg(unix)]
        fn foo() {}
        #[cfg(windows)]
        fn foo() {}

        fn main() {
            foo();
        }
    """, false)

    fun `test do not highlight generic params`() = checkByText("""
        mod foo_bar {
            pub struct Foo<T>(T);
            pub struct Bar<T>(T);
        }

        fn foo<T>() -> <error descr="Unresolved reference: `Foo`">Foo</error><<error descr="Unresolved reference: `Bar`">Bar</error><T>> {
            unimplemented!()
        }
    """)

    fun `test unresolved method reference`() = checkByText("""
        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {
                    unimplemented!();
                }
            }
        }

        fn main() {
            let x = 123.<error descr="Unresolved reference: `foo`">foo</error>();
        }
    """)

    fun `test unresolved method references without quick fix 1`() = checkByText("""
        fn main() {
            let x = 123.foo();
        }
    """, true)

    fun `test unresolved method references without quick fix 2`() = checkByText("""
        fn main() {
            let x = 123.<error descr="Unresolved reference: `foo`">foo</error>();
        }
    """, false)

    fun `test do not highlight method reference with local import`() = checkByText("""
        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {
                    unimplemented!();
                }
            }
        }

        fn main() {
            use foo::Foo;
            123.foo();
        }
    """)

    fun `test do not highlight unresolved path references if name is in scope`() = checkByText("""
        use foo::Foo;

        mod foo {
            pub struct Foo {}
        }

        fn main() {
            Foo
        }
    """, testmark = AutoImportFix.Testmarks.nameInScope)

    fun `test do not highlight unresolved method of trait bound if multiple defs (invalid code)`() = checkByText("""
        mod foo {
            pub trait Trait {
                fn foo(&self) {}
                fn foo(&self) {}
            }
        }
        fn bar<T: foo::Trait>(t: T) {
            t.foo(a); // no error here
        }
    """)

    fun `test no unresolved reference for 'Self' type`() = checkByText("""
        struct Foo;
        impl Foo { fn foo() -> Self {} }
    """, false)

    fun `test unresolved reference for module`() = checkByText("""
        use <error descr="Unresolved reference: `foo`">foo</error>::bar::Foo;
    """, false)

    fun `test unresolved reference for nested module`() = checkByText("""
        mod foo { }
        use foo::<error descr="Unresolved reference: `bar`">bar</error>::Foo;
    """, false)

    fun `test unresolved reference for struct in nested module 1`() = checkByText("""
        mod foo {
            pub mod bar { }
        }
        use foo::bar::<error descr="Unresolved reference: `Foo`">Foo</error>;
    """, false)

    fun `test unresolved reference for struct in nested module 2`() = checkByText("""
        mod foo { }
        use foo::<error descr="Unresolved reference: `bar`">bar</error>::{qux, boo};
    """, false)

    fun `test unresolved reference for function in nested module`() = checkByText("""
        mod foo { }
        fn main() {
            foo::<error descr="Unresolved reference: `bar`">bar</error>::Qux::foobar();
        }
    """, false)

    fun `test no unresolved reference for UFCS with trait`() = checkByText("""
        mod foo {
            pub trait Bar {
                fn baz(&self) {}
            }
        }
        struct S;
        use foo::Bar;
        impl Bar for S {}
        fn main() {
            Bar::baz(&S)
        }
    """)

    fun `test no unresolved reference for UFCS with qualified trait`() = checkByText("""
        mod foo {
            pub trait Bar {
                fn baz(&self) {}
            }
        }
        struct S;
        impl foo::Bar for S {}
        fn main() {
            foo::Bar::baz(&S)
        }
    """)

    fun `test no unresolved reference for a path after incomplete path 1`() = checkByText("""
        use <error descr="Unresolved reference: `foo`">foo</error>::<error descr="identifier expected, got '::'">:</error>:bar;
    """, false)

    fun `test no unresolved reference for a path after incomplete path 2`() = checkByText("""
        mod foo {}
        use foo::<error descr="identifier expected, got '::'">:</error>:bar;
    """, false)

    @MockAdditionalCfgOptions("intellij_rust")
    @ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
    fun `test unknown crate E0463`() = checkByText("""
        extern crate alloc;

        extern crate <error descr="Can't find crate for `litarvan` [E0463]">litarvan</error>;

        #[cfg(intellij_rust)]
        extern crate <error descr="Can't find crate for `unknown_crate1` [E0463]">unknown_crate1</error>;
        #[cfg(not(intellij_rust))]
        extern crate unknown_crate2;
    """)

    private fun checkByText(@Language("Rust") text: String, ignoreWithoutQuickFix: Boolean) {
        val inspection = inspection as RsUnresolvedReferenceInspection
        val defaultValue = inspection.ignoreWithoutQuickFix
        try {
            inspection.ignoreWithoutQuickFix = ignoreWithoutQuickFix
            checkByText(text)
        } finally {
            inspection.ignoreWithoutQuickFix = defaultValue
        }
    }
}
