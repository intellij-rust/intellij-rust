/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import org.intellij.lang.annotations.Language
import org.rust.FileTreeBuilder
import org.rust.RsTestBase
import org.rust.fileTree

abstract class RsLineMarkerProviderTestBase : RsTestBase() {

    private lateinit var lineMarkerTestHelper: LineMarkerTestHelper

    override fun setUp() {
        super.setUp()
        lineMarkerTestHelper = LineMarkerTestHelper(myFixture)
    }

    protected fun doTestByText(@Language("Rust") source: String) {
        lineMarkerTestHelper.doTestByText("lib.rs", source)
    }

    protected fun doTestByFileTree(filePath: String, builder: FileTreeBuilder.() -> Unit) {
        val testProject = fileTree(builder).create()
        lineMarkerTestHelper.doTestFromFile(testProject.file(filePath))
    }
}
