/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.intellij.lang.annotations.Language
import org.rust.MockServerFixture
import org.rust.SkipTestWrapping
import org.rust.cargo.project.model.impl.DEFAULT_EDITION_FOR_TESTS
import org.rust.ide.actions.ShareInPlaygroundActionTest

@SkipTestWrapping
class ShareInPlaygroundIntentionTest : RsIntentionTestBase(ShareInPlaygroundIntention::class) {

    private val mockServerFixture: MockServerFixture = MockServerFixture()

    override fun setUp() {
        super.setUp()
        mockServerFixture.setUp()
    }

    override fun tearDown() {
        mockServerFixture.tearDown()
        super.tearDown()
    }

    fun `test unavailable without selection`() = doUnavailableTest("""
        fn main() {/*caret*/
            println("Hello!")
        }
    """)

    fun `test with selection`() = doTest("""
        <selection>fn main() {
            println("Hello!")
        }</selection>
    """, """
        fn main() {
            println("Hello!")
        }
    """)

    private fun doTest(@Language("Rust") code: String, @Language("Rust") codeToShare: String) {
        InlineFile(code.trimIndent())
        ShareInPlaygroundActionTest.doTest(project, mockServerFixture, codeToShare, DEFAULT_EDITION_FOR_TESTS, "stable", ::launchAction)
    }
}
