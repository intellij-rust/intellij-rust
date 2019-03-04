/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.testFramework.LightPlatformTestCase
import org.intellij.lang.annotations.Language
import org.rust.cargo.project.settings.impl.RustProjectSettingsServiceImpl
import org.rust.cargo.toolchain.RustToolchain
import org.rust.openapiext.elementFromXmlString
import org.rust.openapiext.toXmlString
import java.nio.file.Paths


class RustProjectSettingsServiceTest : LightPlatformTestCase() {
    fun `test serialization`() {
        val service = RustProjectSettingsServiceImpl(LightPlatformTestCase.getProject())
        @Language("XML")
        val text = """
            <State>
              <option name="autoUpdateEnabled" value="false" />
              <option name="cargoCheckArguments" value="--no-default-features" />
              <option name="compileAllTargets" value="false" />
              <option name="doctestInjectionEnabled" value="false" />
              <option name="expandMacros" value="false" />
              <option name="explicitPathToStdlib" value="/stdlib" />
              <option name="showTestToolWindow" value="false" />
              <option name="toolchainHomeDirectory" value="/" />
              <option name="useCargoCheckAnnotator" value="true" />
              <option name="useCargoCheckForBuild" value="true" />
              <option name="useOffline" value="true" />
              <option name="useSkipChildren" value="true" />
            </State>
        """.trimIndent()
        service.loadState(elementFromXmlString(text).deserialize())
        val actual = service.state.serialize()!!.toXmlString()
        assertEquals(text.trimIndent(), actual)

        assertEquals(RustToolchain(Paths.get("/")), service.toolchain)
        assertEquals(false, service.autoUpdateEnabled)
        assertEquals("/stdlib", service.explicitPathToStdlib)
        assertEquals(true, service.useCargoCheckForBuild)
        assertEquals(true, service.useCargoCheckAnnotator)
        assertEquals("--no-default-features", service.cargoCheckArguments)
        assertEquals(false, service.compileAllTargets)
        assertEquals(true, service.useOffline)
        assertEquals(false, service.expandMacros)
        assertEquals(false, service.showTestToolWindow)
        assertEquals(false, service.doctestInjectionEnabled)
        assertEquals(true, service.useSkipChildren)
    }
}
