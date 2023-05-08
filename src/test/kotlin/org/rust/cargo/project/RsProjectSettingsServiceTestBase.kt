/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import org.intellij.lang.annotations.Language
import org.rust.cargo.project.settings.RsProjectSettingsServiceBase
import org.rust.cargo.project.settings.RsProjectSettingsServiceBase.RsProjectSettingsBase
import org.rust.openapiext.elementFromXmlString
import org.rust.openapiext.toXmlString

abstract class RsProjectSettingsServiceTestBase : LightPlatformTestCase() {
    protected inline fun <reified T : RsProjectSettingsBase<T>> RsProjectSettingsServiceBase<T>.loadStateAndCheck(
        @Language("XML") xml: String,
        @Language("XML") expected: String = xml
    ) {
        val element = elementFromXmlString(xml.trimIndent())
        val state = XmlSerializer.deserialize(element, T::class.java)
        loadState(state)
        val actualState = getState()
        val actual = XmlSerializer.serialize(actualState).toXmlString()
        assertEquals(expected.trimIndent(), actual)
    }
}
