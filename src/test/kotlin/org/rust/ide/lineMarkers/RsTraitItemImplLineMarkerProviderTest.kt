/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

/**
 * Tests for Trait member (const, fn, type) Implementation Line Marker
 */
class RsTraitItemImplLineMarkerProviderTest : RsLineMarkerProviderTestBase() {
    fun `test impl`() = doTestByText("""
        trait Foo {         // - Has implementations
            fn foo(&self);  // - Has implementations
            fn bar(&self) { // - Has implementations
                self.foo();
            }
            type T1;        // - Has implementations
            type T2 = ();   // - Has implementations
            const C1: u32;  // - Has implementations
            const C2: u32 = 1;  // - Has implementations
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
            foo     // - Has implementations
            (&self);

            type
            T1      // - Has implementations
            ;

            const
            C1      // - Has implementations
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
