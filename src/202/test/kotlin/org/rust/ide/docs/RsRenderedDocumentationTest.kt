/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import org.rust.lang.core.psi.RsDocCommentImpl
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.doc.docElements

// BACKCOMPAT 2020.1. Merge with RsRenderedDocumentationTestBase
class RsRenderedDocumentationTest : RsRenderedDocumentationTestBase() {

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

    override fun doTest(code: String, expected: String?) {
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
