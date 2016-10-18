package org.rust.ide.annotator

/**
 * Tests for Trait Method Implementation Line Marker
 */
class RustTraitMethodImplLineMarkerProviderTest : RustLineMarkerProviderTestBase() {

    fun testImpl() = doTestByText("""
        trait Foo {
            fn foo(&self);
            fn bar(&self) {
                self.foo();
            }
        }
        struct Bar {}
        impl Foo for Bar {
            fn foo(&self) { // - Implements method in `Foo`
            }
            fn bar(&self) { // - Overrides method in `Foo`
            }
        }
    """)
}
