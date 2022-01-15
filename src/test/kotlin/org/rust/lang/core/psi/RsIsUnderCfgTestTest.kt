/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.intellij.lang.annotations.Language
import org.rust.MockAdditionalCfgOptions
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.isUnderCfgTest

class RsIsUnderCfgTestTest : RsTestBase() {
    fun `test cfg test mod`() = doTest("""
        #[cfg(test)]
        mod tests {
            fn foo() {}
             //^ true
        }
    """)

    fun `test cfg not test mod`() = doTest("""
        #[cfg(not(test))]
        mod tests {
            fn foo() {}
             //^ false
        }
    """)

    fun `test test fn`() = doTest("""
        #[test]
        fn foo() {
            foo;
        } //^ true
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg or option test mod`() = doTest("""
        #[cfg(any(test, intellij_rust))]
        mod tests {
            fn foo() {}
             //^ false
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg and option test mod`() = doTest("""
        #[cfg(all(test, intellij_rust))]
        mod tests {
            fn foo() {}
             //^ true
        }
    """)

    fun `test cfg test out-of-line mod 1`() = doTestByTree("""
    //- main.rs
        #[cfg(test)]
        mod tests;
    //- tests.rs
        fn foo() {}
          //^ true
    """)

    fun `test cfg test out-of-line mod 2`() = doTestByTree("""
    //- main.rs
        mod tests;
    //- tests.rs
        #![cfg(test)]
        fn foo() {}
          //^ true
    """)

    private fun doTest(@Language("Rust") code: String) {
        InlineFile(code)
        doTest()
    }

    private fun doTestByTree(@Language("Rust") code: String) {
        fileTreeFromText(code).createAndOpenFileWithCaretMarker()
        doTest()
    }

    private fun doTest() {
        val (element, data) = findElementAndDataInEditor<RsElement>()

        val expected = when (data) {
            "true" -> true
            "false" -> false
            else -> error("Unknown value `$data`")
        }
        val actual = element.isUnderCfgTest
        assertEquals(expected, actual)
    }
}
