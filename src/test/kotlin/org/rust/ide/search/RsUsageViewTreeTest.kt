/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.usages.UsageViewSettings
import com.intellij.util.xmlb.XmlSerializerUtil
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement

abstract class RsUsageViewTreeTestBase : RsTestBase() {

    protected fun doTestByText(@Language("Rust") code: String, representation: String) {
        InlineFile(code)
        val source = findElementInEditor<RsNamedElement>()

        val textRepresentation = myFixture.getUsageViewTreeTextRepresentation(source)
        assertEquals(representation.trimIndent(), textRepresentation.trimIndent())
    }

    private val originalSettings = UsageViewSettings()
    override fun setUp() {
        super.setUp()
        val settings = UsageViewSettings.instance
        XmlSerializerUtil.copyBean(settings.state, originalSettings)

        settings.isGroupByFileStructure = false
        settings.isGroupByModule = false
        settings.isGroupByPackage = false
        settings.isGroupByUsageType = true
        settings.isGroupByScope = false
    }

    override fun tearDown() {
        UsageViewSettings.instance.loadState(originalSettings)
        super.tearDown()
    }
}
