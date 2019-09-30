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

    fun `test const expr 2`() = stubOnlyTypeInfer("""
    //- foo.rs
        const COUNT: usize = 2;
        pub fn foo(a: i32, b: [i32; (2 * COUNT + 3) << (4 / 2)]) { unimplemented!() }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::foo();
            x;
          //^ ()
        }
    """)

    fun `test const expr in a macro`() = stubOnlyTypeInfer("""
    //- foo.rs
        macro_rules! foo { ($ i:item) => { $ i }; }
        const COUNT: usize = 2;
        foo! { pub fn foo() -> [i32; COUNT] { unimplemented!() } }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::foo();
            x;
          //^ [i32; 2]
        }
    """)
}
