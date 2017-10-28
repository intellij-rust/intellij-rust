/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers


class RsImplsLineMarkerProviderTest : RsLineMarkerProviderTestBase() {

    fun `test no impls`() = doTestByText("""
        // ideally don't want a marker here, but that's costly!
        trait Foo {} // - Has implementations
    """)

    fun testOneImpl() = doTestByText("""
        trait Foo {}  // - Has implementations
        struct Bar {} // - Has implementations
        impl Foo for Bar {}
    """)

    fun testMultipleImpl() = doTestByText("""
        trait Foo {}  // - Has implementations
        mod bar {
            use super::Foo;
            struct Bar {} // - Has implementations
            impl Foo for Bar {}
        }
        mod baz {
            use super::Foo;
            struct Baz {}  // - Has implementations
            impl Foo for Baz {}
        }
    """)

    fun testIconPosition() = doTestByText("""
        ///
        /// Documentation
        ///
        #[warn(non_camel_case_types)]
        trait Foo {}                // - Has implementations
        struct Bar {}               // - Has implementations
        impl Foo for Bar {}
    """)
}
