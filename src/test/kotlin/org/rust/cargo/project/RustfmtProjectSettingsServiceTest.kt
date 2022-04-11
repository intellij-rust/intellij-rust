/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import org.intellij.lang.annotations.Language
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import org.rust.cargo.project.settings.RustfmtProjectSettingsService
import org.rust.cargo.toolchain.RustChannel
import org.rust.openapiext.elementFromXmlString
import org.rust.openapiext.toXmlString

@RunWith(JUnit38ClassRunner::class) // TODO: drop the annotation when issue with Gradle test scanning go away
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
        assertEquals(text, XmlSerializer.serialize(actual).toXmlString())

        assertEquals("--unstable-features", actual.additionalArguments)
        assertEquals(RustChannel.NIGHTLY, actual.channel)
        assertEquals(mapOf("ABC" to "123"), actual.envs)
        assertEquals(true, actual.useRustfmt)
        assertEquals(true, actual.runRustfmtOnSave)
    }

    companion object {
        private fun stateFromXmlString(xml: String): RustfmtProjectSettingsService.RustfmtState {
            val element = elementFromXmlString(xml)
            return XmlSerializer.deserialize(element, RustfmtProjectSettingsService.RustfmtState::class.java)
        }
    }
}
