/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.folding

import org.rust.RsTestBase

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
    fun `test macro`() = doTest()
    fun `test macro2`() = doTest()
    fun `test macro brace arg`() = doTest()
    fun `test use glob list`() = doTest()
    fun `test comments`() = doTest()
    fun `test one liner function`() = doTest()
    fun `test uses`() = doTest()
    fun `test mods`() = doTest()
    fun `test crates`() = doTest()
    fun `test parameter list`() = doTest()
    fun `test custom region`() = doTest()
    fun `test custom region attached to function`() = doTest()
    fun `test custom region in struct`() = doTest()
    fun `test custom region in match expr`() = doTest()
    fun `test custom region in trait`() = doTest()
    fun `test where`() = doTest()
    fun `test extern`() = doTest()

    private fun doTest() {
        myFixture.testFolding("$testDataPath/$fileName")
    }
}
