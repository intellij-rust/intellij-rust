/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ide.lineMarkers.RsLineMarkerProviderTestBase

/**
 * Tests for Trait Method Implementation Line Marker
 */
class RsTraitMethodImplLineMarkerProviderTest : RsLineMarkerProviderTestBase() {

    fun `test impl`() = doTestByText("""
        trait Foo {         // - Has implementations
            fn foo(&self);
            fn bar(&self) {
                self.foo();
            }
        }
        struct Bar {} // - Has implementations
        impl Foo for Bar {
            fn foo(&self) { // - Implements method in `Foo`
            }
            fn bar(&self) { // - Overrides method in `Foo`
            }
        }
    """)

    fun `test icon position`() = doTestByText("""
        trait Foo {         // - Has implementations
            fn foo(&self);
        }
        struct Bar {} // - Has implementations
        impl Foo for Bar {
            ///
            /// Documentation
            ///
            #[warn(non_camel_case_types)]
            fn foo(&self) { // - Implements method in `Foo`
            }
        }
    """)
}
