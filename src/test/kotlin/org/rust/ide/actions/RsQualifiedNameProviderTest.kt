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
    """, listOf("test_package", "test_package::foo", "test_package::foo::inner_function", "test_package::function")
    )

    fun `test struct`() = doTest("""
        struct Bar;
    """, listOf("test_package", "test_package::Bar")
    )
}
