/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.testFramework.LightPlatformTestCase
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.impl.RustProjectSettingsServiceImpl
import org.rust.cargo.toolchain.RustToolchain
import org.rust.openapiext.toXmlString
import java.nio.file.Paths


class RustProjectSettingsServiceTest : LightPlatformTestCase() {
    fun `test serialization`() {
        val service = RustProjectSettingsServiceImpl(LightPlatformTestCase.getProject())
        service.data = RustProjectSettingsService.Data(
            toolchain = RustToolchain(Paths.get("/")),
            autoUpdateEnabled = true,
            explicitPathToStdlib = "/stdlib",
            useCargoCheckForBuild = false,
            useCargoCheckAnnotator = true
        )
        val element = service.state.serialize()!!
        check(element.toXmlString() == """
            <State>
              <option name="explicitPathToStdlib" value="/stdlib" />
              <option name="toolchainHomeDirectory" value="/" />
              <option name="useCargoCheckAnnotator" value="true" />
              <option name="useCargoCheckForBuild" value="false" />
            </State>
        """.trimIndent())
        check(service.state == element.deserialize<RustProjectSettingsServiceImpl.State>())
    }
}
