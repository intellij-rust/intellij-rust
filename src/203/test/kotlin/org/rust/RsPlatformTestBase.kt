/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ThrowableRunnable

// BACKCOMPAT: 2020.2. Merge with `RsTestBase`
abstract class RsPlatformTestBase : BasePlatformTestCase(), RsTestCase {

    abstract override fun getTestDataPath(): String
    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        return super.getTestName(lowercaseFirstLetter)
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        runTestInternal(testRunnable)
    }

    // BACKCOMPAT: 2020.2. Drop it and use `runTestRunnable` directly
    open fun runTestInternal(context: TestContext) {
        super.runTestRunnable(context)
    }
}
