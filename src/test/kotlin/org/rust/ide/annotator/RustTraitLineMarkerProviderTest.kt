package org.rust.ide.annotator

/**
 * Tests for Rust Trait Line Marker Provider
 */
class RustTraitLineMarkerProviderTest : RustLineMarkerProviderTestBase() {

    fun testNoMarker() = doTestByText("""
        trait Foo {}
    """)

    fun testOneImpl() = doTestByText("""
        trait Foo {}  // - Has implementations
        struct Bar {}
        impl Foo for Bar {}
    """)

    fun testMultipleImpl() = doTestByText("""
        trait Foo {}  // - Has implementations
        mod bar {
            use super::Foo;
            struct Bar {}
            impl Foo for Bar {}
        }
        mod baz {
            use super::Foo;
            struct Baz {}
            impl Foo for Baz {}
        }
    """)

}
