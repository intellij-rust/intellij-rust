/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.ide.lineMarkers.LineMarkerTestHelper

abstract class CargoTomlLineMarkerProviderTestBase : RsTestBase() {

    private lateinit var lineMarkerTestHelper: LineMarkerTestHelper

    override fun setUp() {
        super.setUp()
        lineMarkerTestHelper = LineMarkerTestHelper(myFixture)
    }

    protected fun doTestByText(@Language("Toml") source: String) {
        lineMarkerTestHelper.doTestByText("Cargo.toml", source)
    }
}
