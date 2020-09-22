/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import org.rust.TestContext

abstract class RsWithToolchainPlatformTestBase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {

    final override fun runTest() {
        runTestInternal(Unit)
    }

    open fun runTestInternal(context: TestContext) {
        super.runTest()
    }
}
