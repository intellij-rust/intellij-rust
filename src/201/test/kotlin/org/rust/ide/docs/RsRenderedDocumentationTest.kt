/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import org.intellij.lang.annotations.Language

class RsRenderedDocumentationTest : RsRenderedDocumentationTestBase() {

    fun `test several comments`() = doTest("""
        /// Outer comment
        fn add_one(x: i32) -> i32 {
            //^
            //! Inner comment
            x + 1
        }
    """, null)

    override fun doTest(@Language("Rust") code: String, @Language("Html") expected: String?) {
        doTest(code, expected) { originalItem, _ -> generateRenderedDoc(originalItem)?.hideSpecificStyles() }
    }
}
