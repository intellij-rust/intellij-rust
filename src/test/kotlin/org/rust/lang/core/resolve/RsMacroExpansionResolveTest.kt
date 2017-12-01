/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

private const val `$` = '$'

class RsMacroExpansionResolveTest : RsResolveTestBase() {
    fun `test lazy static`() = checkByCode("""
        #[macro_use]
        extern crate lazy_static;

        struct Foo {}
        impl Foo {
            fn new() -> Foo { Foo {} }
            fn bar(&self) {}
        }     //X

        lazy_static! { static ref FOO: Foo = Foo::new(); }

        fn main() {
            FOO.bar()
        }      //^
    """)

    fun `test expand items star`() = checkByCode("""
        macro_rules! if_std {
            ($`$`($`$`i:item)*) => ($`$`(
                #[cfg(feature = "use_std")]
                $`$`i
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^

    """)

    fun `test expand items star with reexport`() = checkByCode("""
        macro_rules! if_std {
            ($`$`($`$`i:item)*) => ($`$`(
                #[cfg(feature = "use_std")]
                $`$`i
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            use Foo as Bar;
        }

        fn main() {
            Bar.bar()
        }      //^

    """)

    fun `test expand items star with reexport from expansion`() = checkByCode("""
        macro_rules! if_std {
            ($`$`($`$`i:item)*) => ($`$`(
                #[cfg(feature = "use_std")]
                $`$`i
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            fn foo() -> Foo { Foo }
            use foo as bar;
        }

        fn main() {
            bar().bar()
        }        //^

    """)
}
