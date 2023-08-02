/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import org.rust.ProjectDescriptor
import org.rust.WithExperimentalFeatures
import org.rust.WithProcMacroRustProjectDescriptor
import org.rust.ide.experiments.RsExperiments

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

    // TODO support attribute macros in `RsRecursiveCallLineMarkerProvider`
    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test function under a proc macro attribute`() = expect<Throwable> {
    doTestByText("""
        use test_proc_macros::attr_as_is;
        #[attr_as_is]
        fn foo() {
            foo();      // - Recursive call
        }
    """)
    }
}
