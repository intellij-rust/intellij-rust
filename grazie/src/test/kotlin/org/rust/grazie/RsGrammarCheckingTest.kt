/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.grazie

import org.rust.ide.annotator.RsAnnotationTestFixture

class RsGrammarCheckingTest : RsGrammarCheckingTestBase() {

    override fun createAnnotationFixture(): RsAnnotationTestFixture =
        RsAnnotationTestFixture(myFixture, inspectionClasses = listOf(inspectionClass), baseFileName = "lib.rs")

    fun `test check literals`() = checkByText("""
        fn foo() {
            let literal = "<TYPO>There is two apples</TYPO>";
            let raw_literal = r"<TYPO>There is two apples</TYPO>";
            let binary_literal = b"<TYPO>There is two apples</TYPO>";
        }
    """)

    fun `test check doc comments`() = checkByText("""
        ///
        /// ```
        /// let literal = "<TYPO>There is two apples</TYPO>";
        /// for i in 1..10 {}
        /// ```
        pub fn foo() {}
    """)
}
