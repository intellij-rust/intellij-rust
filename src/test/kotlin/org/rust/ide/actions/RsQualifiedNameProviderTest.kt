/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

class RsQualifiedNameProviderTest : RsQualifiedNameProviderTestBase() {
    fun `test function`() = doTest("""
        use foo::inner_function;
        mod foo {
            pub fn inner_function() {}
        }

        fn main() {
            inner_function()
          //^
        }
    """, "test_package::foo::inner_function")

    fun `test fields in struct`() = doTest("""
        struct Point {
            x: f32,
            y: f32,
        }

        fn main() {
            let p = Point { x: 0.3, y: 0.4 };
            p.x;
            //^
        }
    """, "test_package::Point::x")

    fun `test impl with func`() = doTest("""
        trait Show {
            fn show(&self) -> String;
        }

        impl Show for i32 {
            fn show(&self) -> String {
                format!("four-byte signed {}", self)
            }
        }

        fn main() {
           42.show();
             //^
        }
""", "test_package::i32::show")

    fun `test struct with constant`() = doTest("""
        struct Foo;

        impl Foo {
            const FOO: u32 = 3;
        }

        fn main() {
            Foo::FOO;
               //^
        }
""", "test_package::Foo::FOO")

    fun `test trait with type alias`() = doTest("""
        trait Number {
            type Kilometers;
            fn get_kilometers(&self) -> Self::Kilometers;
                                            //^
        }
""", "test_package::Number::Kilometers")
}
