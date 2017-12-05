/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.folding

import org.rust.lang.RsTestBase

class RsFoldingBuilderTest : RsTestBase() {
    override val dataPath = "org/rust/ide/folding/fixtures"

    fun `test fn`() = doTest()
    fun `test loops`() = doTest()
    fun `test block expr`() = doTest()
    fun `test impl`() = doTest()
    fun `test impl method`() = doTest()
    fun `test struct`() = doTest()
    fun `test struct expr`() = doTest()
    fun `test trait`() = doTest()
    fun `test trait method`() = doTest()
    fun `test enum`() = doTest()
    fun `test enum variant`() = doTest()
    fun `test mod`() = doTest()
    fun `test match expr`() = doTest()
    fun `test macro brace arg`() = doTest()
    fun `test use glob list`() = doTest()
    fun `test comments`() = doTest()
    fun `test one liner function`() = doTest()
    fun `test uses`() = doTest()
    fun `test mods`() = doTest()
    fun `test crates`() = doTest()

    private fun doTest() {
        myFixture.testFolding("$testDataPath/$fileName")
    }
}
