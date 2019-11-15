/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language
import org.rust.lang.core.completion.RsCompletionTestFixture

abstract class CargoTomlCompletionTestBase : BasePlatformTestCase() {

    protected lateinit var completionFixture: RsCompletionTestFixture

    override fun setUp() {
        super.setUp()
        completionFixture = RsCompletionTestFixture(myFixture, "Cargo.toml")
        completionFixture.setUp()
    }

    override fun tearDown() {
        completionFixture.tearDown()
        super.tearDown()
    }

    protected fun doSingleCompletion(
        @Language("TOML") before: String,
        @Language("TOML") after: String
    ) = completionFixture.doSingleCompletion(before, after)

    protected fun checkNoCompletion(@Language("TOML") code: String) = completionFixture.checkNoCompletion(code)
}
