/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.copyright

import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.psi.UpdateCopyrightFactory
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import java.util.*

class RsCopyrightTest : RsTestBase() {

    fun `test insert copyright`() = doTest("""
        fn main() {}
    """, """
        /*
         * Copyright ${year()}
         * All rights reserved
         */

        fn main() {}
    """)

    fun `test update copyright`() = doTest("""
        /*
         * Copyright 2017
         * All rights reserved
         */

        fn main() {}
    """, """
        /*
         * Copyright ${year()}
         * All rights reserved
         */

        fn main() {}
    """)

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before.trimIndent())
        updateCopyright()
        myFixture.checkResult(after.trimIndent())
    }

    private fun year(): Int = Calendar.getInstance().get(Calendar.YEAR)

    private fun updateCopyright() {
        val options = CopyrightProfile().apply {
            notice = "Copyright \$today.year\nAll rights reserved"
            keyword = "Copyright"
            allowReplaceRegexp = "Copyright"
        }
        val updateCopyright = UpdateCopyrightFactory.createUpdateCopyright(myFixture.project, myFixture.module,
            myFixture.file, options) ?: error("Failed to create copyright update")
        updateCopyright.prepare()
        updateCopyright.complete()
    }
}
