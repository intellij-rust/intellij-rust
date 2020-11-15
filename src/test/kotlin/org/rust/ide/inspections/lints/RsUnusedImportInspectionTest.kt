/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.psi.PsiDocumentManager
import org.intellij.lang.annotations.Language
import org.rust.MockAdditionalCfgOptions
import org.rust.ide.inspections.RsInspectionsTestBase

class RsUnusedImportInspectionTest : RsInspectionsTestBase(RsUnusedImportInspection::class) {
    fun `test unused import`() = checkByText("""
        mod foo {
            pub struct S;
        }

        <warning descr="Unused import: `foo::S`">use foo::S;</warning>
    """)

    fun `test unused import in group`() = checkByText("""
        mod foo {
            pub struct S;
            pub struct T;
        }

        use foo::{S, <warning descr="Unused import: `T`">T</warning>};

        fn bar(_: S) {}
    """)

    fun `test unused import with nested path in group`() = checkByText("""
        mod foo {
            pub struct R;
            pub mod bar {
                pub struct S;
            }
        }

        use foo::{R, <warning descr="Unused import: `bar::S`">bar::S</warning>};
        fn bar(_: R) {}
    """)

    fun `test import used in type context`() = checkByText("""
        mod foo {
            pub struct S;
        }

        use foo::S;

        fn bar(_: S) {}
    """)

    fun `test import used in expr context`() = checkByText("""
        mod foo {
            pub struct S;
            impl S {
                pub fn foo() {}
            }
        }

        use foo::S;

        fn bar() {
            S::foo();
        }
    """)

    fun `test ignore reexport`() = checkByText("""
        pub mod foo {
            pub struct S {}
        }

        pub use foo::S;
        pub(crate) use foo::S;
    """)

    fun `test shadowed path`() = checkByText("""
        mod foo {
            pub struct S {}
        }

        <warning descr="Unused import: `foo::S`">use foo::S;</warning>
        fn bar() {
            let S = 1;
            let x = S;
        }
    """)

    fun `test ignore unresolved usage in child module`() = checkByText("""
        mod foo {
            pub struct S {}
        }

        <warning descr="Unused import: `foo::S`">use foo::S;</warning>
        mod bar {
            fn foo() {
                let x: S;
            }
        }
    """)

    fun `test ignore resolved usage in child module`() = checkByText("""
        mod foo {
            pub struct S {}
        }

        <warning descr="Unused import: `foo::S`">use foo::S;</warning>
        mod bar {
            use super::foo::S;
            fn foo() {
                let x: S;
            }
        }
    """)

    fun `test unused multi-resolve import`() = checkByText("""
        mod foo {
            pub const S: u32 = 1;
            pub struct S {}
        }

        <warning descr="Unused import: `foo::S`">use foo::S;</warning>
    """)

    fun `test used multi-resolve import`() = checkByText("""
        mod foo {
            pub const S: u32 = 1;
            pub struct S {}
        }

        use foo::S;

        fn bar(_: S) {}
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg-disabled usage`() = checkByText("""
        mod foo {
            pub struct S {}
        }

        <warning descr="Unused import: `foo::S`">use foo::S;</warning>
        #[cfg(not(intellij_rust))]
        fn bar() {
            let s: S;
        }
    """)

    fun `test used trait method`() = checkByText("""
        mod foo {
            pub trait Trait {
                fn method(&self);
            }
        }

        use foo::Trait;

        struct S;
        impl foo::Trait for S {
            fn method(&self) {}
        }

        fn bar(s: S) {
            s.method();
        }
    """)

    fun `test used trait associated constant`() = checkByText("""
        mod foo {
            pub trait Trait {
                const FOO: u32;
            }
        }

        use foo::Trait;

        struct S;
        impl foo::Trait for S {
            const FOO: u32 = 1;
        }

        fn bar() {
            let _ = S::FOO;
        }
    """)

    fun `test used trait method with alias`() = checkByText("""
        mod foo {
            pub trait Trait {
                fn method(&self);
            }
        }

        use foo::Trait as T;

        struct S;
        impl foo::Trait for S {
            fn method(&self) {}
        }

        fn bar(s: S) {
            s.method();
        }
    """)

    fun `test unused import with alias`() = checkByText("""
        mod foo {
            pub struct S;
        }

        <warning descr="Unused import: `foo::S as T`">use foo::S as T;</warning>

        fn bar(_: S) {}
    """)

    fun `test used import with alias`() = checkByText("""
        mod foo {
            pub struct S;
        }

        use foo::S as T;

        fn bar(_: T) {}
    """)

    fun `test unused self import`() = checkByText("""
        mod foo {
            pub mod bar {
                pub struct S;
            }
        }

        use foo::bar::{<warning descr="Unused import: `self`">self</warning>};
    """)

    fun `test used self import`() = checkByText("""
        mod foo {
            pub mod bar {
                pub struct S;
            }
        }

        use foo::bar::{self};

        fn bar(_: bar::S) {}
    """)

    fun `test unused star import`() = checkByText("""
        mod foo {}

        <warning descr="Unused import: `foo::*`">use foo::*;</warning>
    """)

    fun `test used star import`() = checkByText("""
        mod foo {
            pub struct S;
        }

        use foo::*;

        fn bar(_: S) {}
    """)

    fun `test used star import in group`() = checkByText("""
        mod foo {
            pub mod bar {
                pub struct S;
            }
        }

        use foo::{bar::*};

        fn bar(_: S) {}
    """)

    fun `test unused import in function`() = checkByText("""
        mod foo {
            pub struct S;
        }

        fn bar() {
            <warning descr="Unused import: `foo::S`">use foo::S;</warning>
        }
    """)

    fun `test used import in function`() = checkByText("""
        mod foo {
            pub struct S;
        }

        fn bar() {
            use foo::S;
            let _: S;
        }
    """)

    fun `test deny`() = checkByText("""
        #![deny(unused_imports)]

        mod foo {
            pub struct S;
        }

        <error descr="Unused import: `foo::S`">use foo::S;</error>
    """)

    fun `test deny group`() = checkByText("""
        #![deny(unused)]

        mod foo {
            pub struct S;
        }

        <error descr="Unused import: `foo::S`">use foo::S;</error>
    """)

    /*fun `test redundant use speck`() = checkByText("""
        mod foo {
            pub struct S;
        }

        <warning descr="Unused import: `foo::S`">use foo::S;</warning>

        fn bar() {
            use foo::S;
            let _: S;
        }
    """)*/
}
