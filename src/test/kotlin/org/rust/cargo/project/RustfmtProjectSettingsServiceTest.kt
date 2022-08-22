/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import org.intellij.lang.annotations.Language
import org.rust.cargo.project.settings.RustfmtProjectSettingsService
import org.rust.cargo.toolchain.RustChannel
import org.rust.openapiext.elementFromXmlString

class RustfmtProjectSettingsServiceTest : LightPlatformTestCase() {
    fun `test serialization`() {
        val service = RustfmtProjectSettingsService(project)

        @Language("XML")
        val text = """
            <RustfmtState>
              <option name="additionalArguments" value="--unstable-features" />
              <option name="channel" value="nightly" />
              <option name="envs">
                <map>
                  <entry key="ABC" value="123" />
                </map>
              </option>
              <option name="runRustfmtOnSave" value="true" />
              <option name="useRustfmt" value="true" />
            </RustfmtState>
        """.trimIndent()
        service.loadState(stateFromXmlString(text))

        val actual = service.state
        assertEquals("--unstable-features", actual.additionalArguments)
        assertEquals(RustChannel.NIGHTLY, actual.channel)
        assertEquals(mapOf("ABC" to "123"), actual.envs)
        assertEquals(true, actual.useRustfmt)
        assertEquals(false, actual.runRustfmtOnSave)
        assertEquals(true, PropertiesComponent.getInstance(project).getBoolean("format.on.save"))
    }

    companion object {
        private fun stateFromXmlString(xml: String): RustfmtProjectSettingsService.RustfmtState {
            val element = elementFromXmlString(xml)
            return XmlSerializer.deserialize(element, RustfmtProjectSettingsService.RustfmtState::class.java)
        }
    }
}
