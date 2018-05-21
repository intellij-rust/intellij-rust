/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

class RsQualifiedNameProviderTest : RsQualifiedNameProviderTestBase() {
    fun `test function`() = doTest("""
        mod foo {
            pub fn inner_function() {}
        }

        fn function() {}
    """, setOf("test_package", "test_package::foo", "test_package::foo::inner_function", "test_package::function"))

    fun `test struct`() = doTest("""
        struct Bar;
    """, setOf("test_package", "test_package::Bar"))

    fun `test trait with func`() = doTest("""
        trait Show {
            fn show(&self) -> String;
        }
""", setOf("test_package", "test_package::Show", "test_package::Show#show"))

    fun `test impl with func`() = doTest("""
        impl Show for i32 {
            fn show(&self) -> String {
                format!("four-byte signed {}", self)
            }
        }
""", setOf("test_package", "test_package::Show#show"))

    fun `test trait with constant`() = doTest("""
        trait Hello {
            const WORLD: u32;
        }
""", setOf("test_package", "test_package::Hello", "test_package::Hello#WORLD"))

    fun `test trait with type alias`() = doTest("""
        trait Number {
            type Integer: i32;
        }
""", setOf("test_package", "test_package::Number", "test_package::Number#Integer"))
}
