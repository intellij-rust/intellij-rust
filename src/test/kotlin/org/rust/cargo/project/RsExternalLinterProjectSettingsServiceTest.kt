/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import org.rust.cargo.project.settings.RsExternalLinterProjectSettingsService
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.toolchain.RustChannel

class RsExternalLinterProjectSettingsServiceTest : RsProjectSettingsServiceTestBase() {
    fun `test serialization`() {
        val service = RsExternalLinterProjectSettingsService(project)
        service.loadStateAndCheck("""
            <RsExternalLinterProjectSettings>
              <option name="additionalArguments" value="--unstable-features" />
              <option name="channel" value="nightly" />
              <option name="envs">
                <map>
                  <entry key="ABC" value="123" />
                </map>
              </option>
              <option name="runOnTheFly" value="true" />
              <option name="tool" value="Clippy" />
            </RsExternalLinterProjectSettings>
        """)
        assertEquals("--unstable-features", service.additionalArguments)
        assertEquals(RustChannel.NIGHTLY, service.channel)
        assertEquals(mapOf("ABC" to "123"), service.envs)
        assertEquals(ExternalLinter.CLIPPY, service.tool)
        assertEquals(true, service.runOnTheFly)
    }
}
