/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import org.rust.MockAdditionalCfgOptions
import org.rust.fileTree

/**
 * Tests for Bench Function Line Marker.
 */
class CargoBenchRunLineMarkerContributorTest : RsLineMarkerProviderTestBase() {
    fun `test simple function`() = doTestByText("""
        #[bench]
        fn has_icon() { assert(true) } // - Bench has_icon
        fn no_icon() { assert(true) }
    """)

    fun `test function in a module`() = doTestByText("""
        mod module { // - Bench module
            #[bench]
            fn has_icon() { assert(true) } // - Bench module::has_icon
            fn no_icon() { assert(true) }
        }
    """)

    fun `test function in a test module`() = doTestByText("""
        #[cfg(test)]
        mod test { // - Bench lib::test
            #[bench]
            fn has_icon() { assert(true) } // - Bench test::has_icon
            fn no_icon() { assert(true) }
        }
    """)

    fun `test function in a tests module`() = doTestByText("""
        #[cfg(test)]
        mod tests { // - Bench lib::tests
            #[bench]
            fn has_icon() { assert(true) } // - Bench tests::has_icon
            fn no_icon() { assert(true) }
        }
    """)

    @MockAdditionalCfgOptions("test")
    fun `test function in a nested tests module`() = doTestByText("""
        #[cfg(test)]
        mod tests { // - Bench lib::tests
            #[cfg(test)]
            mod nested_tests { // - Bench nested_tests
                #[bench]
                fn has_icon() { assert(true) } // - Bench tests::nested_tests::has_icon
                fn no_icon() { assert(true) }
            }
        }
    """)

    fun `test mod decl`() = doTestFromFile(
        "lib.rs",
        fileTree {
            rust("tests.rs", """
                #[bench]
                fn bench() {}
            """)

            rust("no_tests.rs", "")

            rust("lib.rs", """
                mod tests; // - Bench lib::tests
                mod no_tests;
            """)
        }
    )

    fun `test function in a module with test function`() = doTestByText("""
        #[cfg(test)]
        mod module { // - Test module
            #[bench]
            fn has_bench_icon() { assert(true) } // - Bench module::has_bench_icon
            #[test]
            fn has_test_icon() { assert(true) } // - Test module::has_test_icon
            fn no_icon() { assert(true) }
        }
    """)
}
