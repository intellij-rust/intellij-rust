/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class RsStubOnlyTypeInferenceTest : RsTypificationTestBase() {

    fun `test const expr`() = stubOnlyTypeInfer("""
    //- foo.rs
        const COUNT: usize = 2;
        pub fn foo() -> [i32; (2 * COUNT + 3) << (4 / 2)] { unimplemented!() }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::foo();
            x;
          //^ [i32; 28]
        }
    """)
}
