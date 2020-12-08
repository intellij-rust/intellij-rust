/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.rust.MockAdditionalCfgOptions
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsCfgAttrTypeInferenceTest : RsTypificationTestBase() {
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test function parameter under cfg 1`() = testExpr("""
        fn foo(
            #[cfg(intellij_rust)]      a: u8,
            #[cfg(not(intellij_rust))] a: i8,
        ) {}
        fn main() {
            let a = 0;
            foo(a);
            a;
        } //^ u8
    """)

    fun `test function parameter under cfg 2`() = testExpr("""
        fn foo(
            #[cfg(intellij_rust)]      a: u8,
            #[cfg(not(intellij_rust))] a: i8,
        ) {}
        fn main() {
            let a = 0;
            foo(a);
            a;
        } //^ i8
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test infer type of derivable trait method call 1`() = stubOnlyTypeInfer("""
    //- main.rs
        #[cfg_attr(intellij_rust, derive(Clone))]
        struct Foo;

        fn bar(foo: Foo) {
            let foo2 = foo.clone();
            foo2;
           //^ Foo
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test infer type of derivable trait method call 2`() = stubOnlyTypeInfer("""
    //- main.rs
        #[cfg_attr(not(intellij_rust), derive(Clone))]
        struct Foo;

        fn bar(foo: Foo) {
            let foo2 = foo.clone();
            foo2;
           //^ <unknown>
        }
    """)
}
