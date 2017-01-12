package org.rust.ide.annotator

/**
 * Tests for Rust Recursvice Call Line Marker Provider
 */
class RsRecursiveCallLineMarkerProviderTest : RsLineMarkerProviderTestBase() {

    fun testFunction() = doTestByText("""
        fn foo() {
            foo();      // - Recursive call
        }
    """)

    fun testAssocFunction() = doTestByText("""
        struct Foo {}
        impl Foo {
            fn foo() {
                Foo::foo();      // - Recursive call
            }
        }
    """)

    fun testMethod() = doTestByText("""
        struct Foo {}
        impl Foo {
            fn foo(&self) {
                self.foo();      // - Recursive call
            }
        }
    """)

    fun testNamesCollision() = doTestByText("""
        fn foo() {}
        struct Foo {}
        impl Foo {
            fn foo() {
                foo();  // It's the high-level function, no marker
            }
        }
    """)

    fun testIgnoreTransitive() = doTestByText("""
        fn foo() {
            bar();      // Doesn't count
        }
        fn bar() {
            foo();      // Doesn't count
        }
    """)

    fun testMultiple() = doTestByText("""
        fn increment(v: u32) -> u32 {
            increment(increment(1))     // - Recursive call
        }
    """)
}
