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

    fun testTraitBoundsAndObjects() = doTestByText("""
        trait Foo {}
        fn bar<T: Foo>(x: T) {}     // Doesn't count
        fn baz(x: &Foo) {}          // Doesn't count
    """)

    fun testIconPosition() = doTestByText("""
        ///
        /// Documentation
        ///
        #[warn(non_camel_case_types)]
        trait Foo {}                // - Has implementations
        struct Bar {}
        impl Foo for Bar {}
    """)
}
