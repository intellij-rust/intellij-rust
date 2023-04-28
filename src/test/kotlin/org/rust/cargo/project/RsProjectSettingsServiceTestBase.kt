/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import org.intellij.lang.annotations.Language
import org.rust.cargo.project.settings.RsProjectSettingsServiceBase
import org.rust.openapiext.elementFromXmlString
import org.rust.openapiext.toXmlString

abstract class RsProjectSettingsServiceTestBase<T : RsProjectSettingsServiceBase.RsProjectSettingsBase<T>>(
    private val settingsClass: Class<T>
) : LightPlatformTestCase() {
    protected fun RsProjectSettingsServiceBase<T>.loadStateAndCheck(
        @Language("XML") xml: String,
        @Language("XML") expected: String = xml
    ) {
        val element = elementFromXmlString(xml.trimIndent())
        val state = XmlSerializer.deserialize(element, settingsClass)
        loadState(state)
        val actual = XmlSerializer.serialize(state).toXmlString()
        assertEquals(expected.trimIndent(), actual)
    }
}
