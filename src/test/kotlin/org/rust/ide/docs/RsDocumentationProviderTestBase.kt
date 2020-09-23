/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.RsDocCommentImpl
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.doc.docElements

abstract class RsDocumentationProviderTestBase : RsDocumentationProviderTest() {

    fun `test outer comment`() = doTest("""
        /// Adds one to the number given.
        ///
        /// # Examples
        ///
        /// Some text
        ///
        fn add_one(x: i32) -> i32 {
            //^
            x + 1
        }
    """, """
        <p>Adds one to the number given.</p><h2>Examples</h2><p>Some text</p>
    """)

    fun `test inner comment`() = doTest("""
        fn add_one(x: i32) -> i32 {
            //^
            //! Inner comment
            x + 1
        }
    """, """
        <p>Inner comment</p>
    """)

    fun `test several comments`() = doTest("""
        /// Outer comment
        fn add_one(x: i32) -> i32 {
            //^
            //! Inner comment
            x + 1
        }
    """, """
        <p>Outer comment</p>
        <p>Inner comment</p>
    """)

    protected fun doTest(@Language("Rust") code: String, @Language("Html") expected: String?) {
        doTest(code, expected) { originalItem, _ ->
            (originalItem as? RsDocAndAttributeOwner)
                ?.docElements()
                ?.filterIsInstance<RsDocCommentImpl>()
                ?.mapNotNull { generateRenderedDoc(it) }
                ?.joinToString("\n")
                ?.hideSpecificStyles()
        }
    }
}
