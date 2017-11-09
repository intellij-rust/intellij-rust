/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.intellij.lang.annotations.Language
import org.rust.ide.annotator.RsAnnotatorTestBase

// Most tests for this fix are located in `ImplementMembersFixTest`
class ImplementMembersFixAvailabilityTest : RsAnnotatorTestBase() {

    fun `test 'implement members' fix available`() = checkFixAvailable("Implement members", """
        trait T {
            fn f1();
        }
        struct S;
        impl T for S/*caret*/ {}
    """)

    fun checkFixAvailable(fixName: String, @Language("Rust") code: String ) {
        InlineFile(code)
        myFixture.findSingleIntention(fixName)
    }
}
