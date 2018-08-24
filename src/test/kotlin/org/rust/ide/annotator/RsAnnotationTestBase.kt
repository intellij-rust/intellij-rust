/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.openapiext.Testmark

abstract class RsAnnotationTestBase : RsTestBase() {
    protected fun doTest(vararg additionalFilenames: String) {
        myFixture.testHighlighting(fileName, *additionalFilenames)
    }

    protected fun checkInfo(@Language("Rust") text: String)  =
        checkByText(text, checkWarn = false, checkWeakWarn = false, checkInfo = true)

    protected fun checkWarnings(@Language("Rust") text: String) =
        checkByText(text, checkWarn = true, checkWeakWarn = true, checkInfo = false)

    protected fun checkErrors(@Language("Rust") text: String) =
        checkByText(text, checkWarn = false, checkWeakWarn = false, checkInfo = false)

    protected fun checkByText(
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = check(text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByText,
        testmark = testmark)

    protected fun checkFixByText(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = checkFix(fixName, before, after,
        configure = this::configureByText,
        checkBefore = { myFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn) },
        checkAfter = this::checkByText,
        testmark = testmark)

    protected fun checkFixByTextWithoutHighlighting(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        testmark: Testmark? = null
    ) = checkFix(fixName, before, after,
        configure = this::configureByText,
        checkBefore = {},
        checkAfter = this::checkByText,
        testmark = testmark)

    protected fun checkByFileTree(
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = check(text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByFileTree,
        testmark = testmark)

    protected fun checkFixByFileTree(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = checkFix(fixName, before, after,
        configure = this::configureByFileTree,
        checkBefore = { myFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn) },
        checkAfter = this::checkByFileTree,
        testmark = testmark)

    protected fun checkFixIsUnavailable(
        fixName: String,
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = checkFixIsUnavailable(fixName, text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByText,
        testmark = testmark)

    protected fun checkFixIsUnavailableByFileTree(
        fixName: String,
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = checkFixIsUnavailable(fixName, text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByFileTree,
        testmark = testmark)

    private fun check(
        @Language("Rust") text: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        configure: (String) -> Unit,
        testmark: Testmark? = null
    ) {
        val action: () -> Unit = {
            configure(text)
            myFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn)
        }
        testmark?.checkHit(action) ?: action()
    }

    private fun checkFix(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        configure: (String) -> Unit,
        checkBefore: () -> Unit,
        checkAfter: (String) -> Unit,
        testmark: Testmark? = null
    ) {
        val action: () -> Unit = {
            configure(before)
            checkBefore()
            applyQuickFix(fixName)
            checkAfter(after)
        }
        testmark?.checkHit(action) ?: action()
    }

    private fun checkFixIsUnavailable(
        fixName: String,
        @Language("Rust") text: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        configure: (String) -> Unit,
        testmark: Testmark? = null
    ) {
        check(text, checkWarn, checkInfo, checkWeakWarn, configure, testmark)
        check(myFixture.filterAvailableIntentions(fixName).isEmpty()) {
            "Fix $fixName should not be possible to apply."
        }
    }

    private fun checkByText(text: String) {
        myFixture.checkResult(replaceCaretMarker(text.trimIndent()))
    }

    private fun checkByFileTree(text: String) {
        fileTreeFromText(replaceCaretMarker(text)).check(myFixture)
    }

    protected fun checkDontTouchAstInOtherFiles(@Language("Rust") text: String, checkInfo: Boolean = false, filePath: String? = null) {
        fileTreeFromText(text).create()
        val testFilePath = filePath ?: "main.rs"
        (myFixture as CodeInsightTestFixtureImpl) // meh
                    .setVirtualFileFilter { !it.path.endsWith(testFilePath) }

        myFixture.configureFromTempProjectFile(testFilePath)
        myFixture.testHighlighting(false, checkInfo, false)
    }
}
