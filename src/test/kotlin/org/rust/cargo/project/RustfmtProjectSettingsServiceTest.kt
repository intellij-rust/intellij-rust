/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import org.rust.cargo.project.settings.RustfmtProjectSettingsService
import org.rust.cargo.toolchain.RustChannel

class RustfmtProjectSettingsServiceTest : RsProjectSettingsServiceTestBase() {
    fun `test serialization`() {
        val service = RustfmtProjectSettingsService(project)
        service.loadStateAndCheck("""
            <RustfmtProjectSettings>
              <option name="additionalArguments" value="--unstable-features" />
              <option name="channel" value="nightly" />
              <option name="envs">
                <map>
                  <entry key="ABC" value="123" />
                </map>
              </option>
              <option name="runRustfmtOnSave" value="true" />
              <option name="useRustfmt" value="true" />
            </RustfmtProjectSettings>
        """)
        assertEquals("--unstable-features", service.additionalArguments)
        assertEquals(RustChannel.NIGHTLY, service.channel)
        assertEquals(mapOf("ABC" to "123"), service.envs)
        assertEquals(true, service.useRustfmt)
        assertEquals(true, service.runRustfmtOnSave)
    }
}
