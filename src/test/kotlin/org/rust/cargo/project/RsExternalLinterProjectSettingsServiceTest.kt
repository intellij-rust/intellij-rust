/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import org.rust.cargo.project.settings.RsExternalLinterProjectSettingsService
import org.rust.cargo.project.settings.RsExternalLinterProjectSettingsService.RsExternalLinterProjectSettings
import org.rust.cargo.toolchain.ExternalLinter

class RsExternalLinterProjectSettingsServiceTest : RsProjectSettingsServiceTestBase<RsExternalLinterProjectSettings>(
    RsExternalLinterProjectSettings::class.java
) {
    fun `test serialization`() {
        val service = RsExternalLinterProjectSettingsService(project)
        service.loadStateAndCheck("""
            <RsExternalLinterProjectSettings>
              <option name="additionalArguments" value="--unstable-features" />
              <option name="runOnTheFly" value="true" />
              <option name="tool" value="Clippy" />
            </RsExternalLinterProjectSettings>
        """)
        assertEquals("--unstable-features", service.additionalArguments)
        assertEquals(ExternalLinter.CLIPPY, service.tool)
        assertEquals(true, service.runOnTheFly)
    }
}
