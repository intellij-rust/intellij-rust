package org.rust.ide.annotator

/**
 * Tests for Test Function Line Marker
 */
class RsTestFunctionLineMarkerProviderTest : RsLineMarkerProviderTestBase() {
    fun `test simple function`() = doTestByText("""
        #[test]
        fn has_icon() {assert(true)} // - Run Test

        fn no_icon() {assert(true)}
    """)

    fun `test function in a module`() = doTestByText("""
        mod module {
            #[test]
            fn has_icon() {assert(true)} // - Run Test

            fn no_icon() {assert(true)}
        }
    """)

    fun `test function in a test module`() = doTestByText("""
        #[cfg(test)]
        mod test {
            #[test]
            fn has_icon() {assert(true)} // - Run Test

            fn no_icon() {assert(true)}
        }
    """)
}
