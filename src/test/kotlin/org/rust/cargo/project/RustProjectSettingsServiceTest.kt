/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.RustProjectSettingsService.MacroExpansionEngine
import org.rust.cargo.toolchain.RsLocalToolchain
import java.nio.file.Paths

class RustProjectSettingsServiceTest : RsProjectSettingsServiceTestBase() {

    fun `test serialization`() {
        val service = RustProjectSettingsService(project)
        service.loadStateAndCheck("""
            <RustProjectSettings>
              <option name="autoUpdateEnabled" value="false" />
              <option name="compileAllTargets" value="false" />
              <option name="doctestInjectionEnabled" value="false" />
              <option name="explicitPathToStdlib" value="/stdlib" />
              <option name="externalLinter" value="Clippy" />
              <option name="externalLinterArguments" value="--no-default-features" />
              <option name="macroExpansionEngine" value="DISABLED" />
              <option name="runExternalLinterOnTheFly" value="true" />
              <option name="toolchainHomeDirectory" value="/" />
              <option name="useOffline" value="true" />
            </RustProjectSettings>
        """)
        assertEquals(RsLocalToolchain(Paths.get("/")), service.toolchain)
        assertEquals(false, service.autoUpdateEnabled)
        assertEquals("/stdlib", service.explicitPathToStdlib)
        assertEquals(false, service.compileAllTargets)
        assertEquals(true, service.useOffline)
        assertEquals(MacroExpansionEngine.DISABLED, service.macroExpansionEngine)
        assertEquals(false, service.doctestInjectionEnabled)
    }

    fun `test serialization with old fields`() {
        val service = RustProjectSettingsService(project)
        service.loadStateAndCheck("""
            <RustProjectSettings>
              <option name="autoUpdateEnabled" value="false" />
              <option name="compileAllTargets" value="false" />
              <option name="doctestInjectionEnabled" value="false" />
              <option name="explicitPathToStdlib" value="/stdlib" />
              <option name="externalLinter" value="Clippy" />
              <option name="externalLinterArguments" value="--no-default-features" />
              <option name="macroExpansionEngine" value="DISABLED" />
              <option name="runExternalLinterOnTheFly" value="true" />
              <option name="runRustfmtOnSave" value="true" /> <!-- Old field -->
              <option name="toolchainHomeDirectory" value="/" />
              <option name="useOffline" value="true" />
              <option name="useRustfmt" value="true" /> <!-- Old field -->
            </RustProjectSettings>
        """, """
            <RustProjectSettings>
              <option name="autoUpdateEnabled" value="false" />
              <option name="compileAllTargets" value="false" />
              <option name="doctestInjectionEnabled" value="false" />
              <option name="explicitPathToStdlib" value="/stdlib" />
              <option name="externalLinter" value="Clippy" />
              <option name="externalLinterArguments" value="--no-default-features" />
              <option name="macroExpansionEngine" value="DISABLED" />
              <option name="runExternalLinterOnTheFly" value="true" />
              <option name="toolchainHomeDirectory" value="/" />
              <option name="useOffline" value="true" />
            </RustProjectSettings>
        """)
    }
}
