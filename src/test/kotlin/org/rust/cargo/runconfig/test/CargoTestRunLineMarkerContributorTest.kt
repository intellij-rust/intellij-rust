/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import org.rust.ide.lineMarkers.RsLineMarkerProviderTestBase

/**
 * Tests for Test Function Line Marker
 */
class CargoTestRunLineMarkerContributorTest : RsLineMarkerProviderTestBase() {
    fun `test simple function`() = doTestByText("""
        #[test]
        fn has_icon() {assert(true)} // - Test has_icon

        fn no_icon() {assert(true)}
    """)

    fun `test function in a module`() = doTestByText("""
        mod module { // - Test module
            #[test]
            fn has_icon() {assert(true)} // - Test module::has_icon

            fn no_icon() {assert(true)}
        }
    """)

    fun `test function in a test module`() = doTestByText("""
        #[cfg(test)]
        mod test { // - Test lib::test
            #[test]
            fn has_icon() {assert(true)} // - Test test::has_icon

            fn no_icon() {assert(true)}
        }
    """)

    fun `test function in a tests module`() = doTestByText("""
        #[cfg(test)]
        mod tests { // - Test lib::tests
            #[test]
            fn has_icon() {assert(true)} // - Test tests::has_icon

            fn no_icon() {assert(true)}
        }
    """)
}
