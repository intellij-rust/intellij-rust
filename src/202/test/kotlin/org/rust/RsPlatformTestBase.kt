/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class RsPlatformTestBase : BasePlatformTestCase(), RsTestCase {

    abstract override fun getTestDataPath(): String
    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        return super.getTestName(lowercaseFirstLetter)
    }

    override fun runTest() {
        runTestInternal(Unit)
    }

    open fun runTestInternal(context: TestContext) {
        super.runTest()
    }
}
