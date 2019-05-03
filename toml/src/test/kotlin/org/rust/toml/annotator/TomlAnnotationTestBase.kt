/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.annotator

import com.intellij.openapiext.Testmark
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText
import org.rust.toml.CargoTomlTestBase

abstract class TomlAnnotationTestBase : CargoTomlTestBase() {

    protected lateinit var annotationFixture: TomlAnnotationTestFixture

    override fun setUp() {
        super.setUp()
        annotationFixture = createAnnotationFixture()
        annotationFixture.setUp()
    }

    override fun tearDown() {
        annotationFixture.tearDown()
        super.tearDown()
    }

    protected abstract fun createAnnotationFixture(): TomlAnnotationTestFixture

    protected fun checkHighlighting(@Language("TOML") text: String) = annotationFixture.checkHighlighting(text)
    protected fun checkInfo(@Language("TOML") text: String) = annotationFixture.checkInfo(text)
    protected fun checkWarnings(@Language("TOML") text: String) = annotationFixture.checkWarnings(text)
    protected fun checkErrors(@Language("TOML") text: String) = annotationFixture.checkErrors(text)

    protected fun checkByText(
        @Language("TOML") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        testmark: Testmark? = null
    ) = annotationFixture.checkByText(text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting, testmark)

    protected fun checkFixByText(
        fixName: String,
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = annotationFixture.checkFixByText(fixName, before, after, checkWarn, checkInfo, checkWeakWarn, testmark)

    protected fun checkFixByTextWithoutHighlighting(
        fixName: String,
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        testmark: Testmark? = null
    ) = annotationFixture.checkFixByTextWithoutHighlighting(fixName, before, after, testmark)

    protected fun checkByFileTree(
        @Language("TOML") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        stubOnly: Boolean = true,
        testmark: Testmark? = null
    ) = annotationFixture.checkByFileTree(text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting, stubOnly, testmark)

    protected fun checkFixByFileTree(
        fixName: String,
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        stubOnly: Boolean = true,
        testmark: Testmark? = null
    ) = annotationFixture.checkFixByFileTree(fixName, before, after, checkWarn, checkInfo, checkWeakWarn, stubOnly, testmark)

    protected fun checkFixByFileTreeWithoutHighlighting(
        fixName: String,
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        stubOnly: Boolean = true,
        testmark: Testmark? = null
    ) = annotationFixture.checkFixByFileTreeWithoutHighlighting(fixName, before, after, stubOnly, testmark)

    protected fun checkFixIsUnavailable(
        fixName: String,
        @Language("TOML") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        testmark: Testmark? = null
    ) = annotationFixture.checkFixIsUnavailable(fixName, text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting, testmark)

    protected fun checkFixIsUnavailableByFileTree(
        fixName: String,
        @Language("TOML") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        stubOnly: Boolean = true,
        testmark: Testmark? = null
    ) = annotationFixture.checkFixIsUnavailableByFileTree(fixName, text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting, stubOnly, testmark)
}
