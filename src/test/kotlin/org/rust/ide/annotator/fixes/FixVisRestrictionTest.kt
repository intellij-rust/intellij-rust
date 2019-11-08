/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class FixVisRestrictionTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    fun `test fix visibility restriction 1`() = checkFixByText("Fix visibility restriction", """
        mod foo {
            pub(/*some comment*/<error descr="Incorrect visibility restriction [E0704]">foo/*caret*/</error>) fn bar() {}
        }
    """, """
        mod foo {
            pub(/*some comment*/ in foo/*caret*/) fn bar() {}
        }
    """)

    fun `test fix visibility restriction 2`() = checkFixByText("Fix visibility restriction", """
        mod foo {
            pub(/*some comment*/<error descr="Incorrect visibility restriction [E0704]">super::foo/*caret*/</error>) fn bar() {}
        }
    """, """
        mod foo {
            pub(/*some comment*/ in super::foo/*caret*/) fn bar() {}
        }
    """)

    fun `test do not annotate short version of visibility restriction`() {
        for (restriction in listOf("crate", "self", "super")) {
            checkFixIsUnavailable("Fix visibility restriction", """
                mod foo {
                    pub($restriction/*caret*/) fn bar() {}
                }
            """)
        }
    }
}
