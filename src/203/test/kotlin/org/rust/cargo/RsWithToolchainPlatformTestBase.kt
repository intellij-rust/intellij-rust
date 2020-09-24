/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import org.rust.TestContext

// BACKCOMPAT: 2020.2. Merge with `RsWithToolchainTestBase`
abstract class RsWithToolchainPlatformTestBase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {

    final override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        runTestInternal(testRunnable)
    }

    // BACKCOMPAT: 2020.2. Drop it and use `runTestRunnable` directly
    open fun runTestInternal(context: TestContext) {
        super.runTestRunnable(context)
    }
}
