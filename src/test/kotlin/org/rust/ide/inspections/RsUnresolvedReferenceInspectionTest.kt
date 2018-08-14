/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.intellij.lang.annotations.Language

class RsUnresolvedReferenceInspectionTest : RsInspectionsTestBase(RsUnresolvedReferenceInspection()) {

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

    private fun checkByText(@Language("Rust") text: String, ignoreWithoutQuickFix: Boolean) {
        val defaultValue = (inspection as RsUnresolvedReferenceInspection).ignoreWithoutQuickFix
        try {
            inspection.ignoreWithoutQuickFix = ignoreWithoutQuickFix
            checkByText(text)
        } finally {
            inspection.ignoreWithoutQuickFix = defaultValue
        }
    }
}
