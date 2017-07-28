/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import org.rust.ide.annotator.RsLineMarkerProviderTestBase

/**
 * Tests for Test Function Line Marker
 */
class CargoTestRunLineMarkerContributorTest : RsLineMarkerProviderTestBase() {
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
        mod test { // - Run Tests
            #[test]
            fn has_icon() {assert(true)} // - Run Test

            fn no_icon() {assert(true)}
        }
    """)
}
