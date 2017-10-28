/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

/**
 * Tests for Rust Recursive Call Line Marker Provider
 */
class RsRecursiveCallLineMarkerProviderTest : RsLineMarkerProviderTestBase() {

    fun `test function`() = doTestByText("""
        fn foo() {
            foo();      // - Recursive call
        }
    """)

    fun `test assoc function`() = doTestByText("""
        struct Foo {} // - Has implementations
        impl Foo {
            fn foo() {
                Foo::foo();      // - Recursive call
            }
        }
    """)

    fun `test method`() = doTestByText("""
        struct Foo {} // - Has implementations
        impl Foo {
            fn foo(&self) {
                self.foo();      // - Recursive call
            }
        }
    """)

    fun `test names collision`() = doTestByText("""
        fn foo() {}
        struct Foo {} // - Has implementations
        impl Foo {
            fn foo() {
                foo();  // It's the high-level function, no marker
            }
        }
    """)

    fun `test ignore transitive`() = doTestByText("""
        fn foo() {
            bar();      // Doesn't count
        }
        fn bar() {
            foo();      // Doesn't count
        }
    """)

    fun `test multiple`() = doTestByText("""
        fn increment(v: u32) -> u32 {
            increment(increment(1))     // - Recursive call
        }
    """)
}
