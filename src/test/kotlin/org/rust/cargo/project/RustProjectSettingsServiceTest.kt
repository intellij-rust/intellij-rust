/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.testFramework.LightPlatformTestCase
import org.intellij.lang.annotations.Language
import org.rust.cargo.project.settings.RustProjectSettingsService
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
              <option name="compileAllTargets" value="false" />
              <option name="expandMacros" value="false" />
              <option name="explicitPathToStdlib" value="/stdlib" />
              <option name="toolchainHomeDirectory" value="/" />
              <option name="useCargoCheckAnnotator" value="true" />
              <option name="useCargoCheckForBuild" value="false" />
              <option name="useOfflineForCargoCheck" value="true" />
              <option name="useSkipChildren" value="true" />
            </State>
        """.trimIndent()
        service.loadState(elementFromXmlString(text).deserialize())
        val actual = service.state.serialize()!!.toXmlString()
        check(actual == text.trimIndent()) {
            "Expected:\n$text\nActual:\n$actual"
        }

        check(service.data == RustProjectSettingsService.Data(
            toolchain = RustToolchain(Paths.get("/")),
            autoUpdateEnabled = false,
            explicitPathToStdlib = "/stdlib",
            useCargoCheckForBuild = false,
            useCargoCheckAnnotator = true,
            compileAllTargets = false,
            useOfflineForCargoCheck = true,
            expandMacros = false,
            useSkipChildren = true
        ))
    }
}
