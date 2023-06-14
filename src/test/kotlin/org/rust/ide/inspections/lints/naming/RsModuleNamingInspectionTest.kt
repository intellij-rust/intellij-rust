/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints.naming

import org.rust.SkipTestWrapping
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.lints.RsModuleNamingInspection

class RsModuleNamingInspectionTest : RsInspectionsTestBase(RsModuleNamingInspection::class) {
    fun `test modules`() = checkByText("""
        mod module_ok {}
        mod <warning descr="Module `moduleA` should have a snake case name such as `module_a`">moduleA</warning> {}
    """)

    fun `test modules suppression`() = checkByText("""
        #[allow(non_snake_case)]
        mod moduleA {}
    """)

    fun `test modules suppression bad style 1`() = checkByText("""
        #[allow(bad_style)]
        mod moduleA {}
    """)

    fun `test modules suppression bad style 2`() = checkByFileTree("""
    //- main.rs
        #![allow(bad_style)]

        mod x;
    //- x.rs
        mod moduleA/*caret*/ {}
    """)

    fun `test modules suppression nonstandard style`() = checkByText("""
        #[allow(nonstandard_style)]
        mod moduleA {}
    """)

    @SkipTestWrapping // TODO support `RenameFix` in macros
    fun `test modules fix`() = checkFixByText("Rename to `mod_foo`", """
        mod <warning descr="Module `modFoo` should have a snake case name such as `mod_foo`">modF<caret>oo</warning> {
            pub const ONE: u32 = 1;
        }
        fn use_mod() {
            let a = modFoo::ONE;
        }
    """, """
        mod mod_foo {
            pub const ONE: u32 = 1;
        }
        fn use_mod() {
            let a = mod_foo::ONE;
        }
    """, preview = null)

    fun `test module not support case`() = checkByText("""
        mod 模块名 {}
    """)

    fun `test mod decl 1`() = checkFixByFileTreeWithoutHighlighting("Rename to `foo`", """
    //- main.rs
        fn main() {
            Foo::func();
        }
        mod Foo/*caret*/;
    //- Foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        fn main() {
            foo::func();
        }
        mod foo/*caret*/;
    //- foo.rs
        pub fn func() {}
    """, preview = null)

    fun `test mod decl 2`() = checkFixByFileTreeWithoutHighlighting("Rename to `foo`", """
    //- main.rs
        fn main() {
            Foo::func();
        }
        mod Foo/*caret*/;
    //- Foo/mod.rs
        pub fn func() {}
    """, """
    //- main.rs
        fn main() {
            foo::func();
        }
        mod foo/*caret*/;
    //- foo/mod.rs
        pub fn func() {}
    """, preview = null)
}
