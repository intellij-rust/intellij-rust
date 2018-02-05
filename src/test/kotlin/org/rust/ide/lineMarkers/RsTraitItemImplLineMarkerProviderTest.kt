/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import org.rust.ide.lineMarkers.RsLineMarkerProviderTestBase

/**
 * Tests for Trait member (const, fn, type) Implementation Line Marker
 */
class RsTraitItemImplLineMarkerProviderTest : RsLineMarkerProviderTestBase() {

    fun `test impl`() = doTestByText("""
        trait Foo {         // - Has implementations
            fn foo(&self);
            fn bar(&self) {
                self.foo();
            }
            type T1;
            type T2 = ();
            const C1: u32;
            const C2: u32 = 1;
        }
        struct Bar {} // - Has implementations
        impl Foo for Bar {
            fn foo(&self) { // - Implements method in `Foo`
            }
            fn bar(&self) { // - Overrides method in `Foo`
            }
            type T1 = (); // - Implements type in `Foo`
            type T2 = (); // - Overrides type in `Foo`
            const C1: u32 = 1; // - Implements constant in `Foo`
            const C2: u32 = 1; // - Overrides constant in `Foo`
        }
    """)

    fun `test icon position`() = doTestByText("""
        trait
        Foo // - Has implementations
        {
            fn
            foo
            (&self);

            type
            T1
            ;

            const
            C1
            : u32;
        }
        struct
        Bar // - Has implementations
        {}
        impl Foo for Bar {
            ///
            /// Documentation
            ///
            #[warn(non_camel_case_types)]
            fn
            foo // - Implements method in `Foo`
            (&self) {
            }

            type
            T1 // - Implements type in `Foo`
            = ();

            const
            C1 // - Implements constant in `Foo`
            : u32 = 1;
        }
    """)
}
