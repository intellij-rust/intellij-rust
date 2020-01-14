/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.testFramework.LightPlatformTestCase
import org.intellij.lang.annotations.Language
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.RustProjectSettingsService.MacroExpansionEngine
import org.rust.cargo.project.settings.impl.RustProjectSettingsServiceImpl
import org.rust.cargo.project.settings.impl.XML_FORMAT_VERSION
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.toolchain.RustToolchain
import org.rust.openapiext.elementFromXmlString
import org.rust.openapiext.toXmlString
import java.nio.file.Paths

class RustProjectSettingsServiceTest : LightPlatformTestCase() {

    fun `test serialization`() {
        val service = RustProjectSettingsServiceImpl(project)

        @Language("XML")
        val text = """
            <RustProjectSettings>
              <option name="autoUpdateEnabled" value="false" />
              <option name="cargoFeatures" value="NoDefault" />
              <option name="cargoFeaturesAdditional">
                <list>
                  <option value="foo" />
                  <option value="bar" />
                </list>
              </option>
              <option name="compileAllTargets" value="false" />
              <option name="doctestInjectionEnabled" value="false" />
              <option name="explicitPathToStdlib" value="/stdlib" />
              <option name="externalLinter" value="Clippy" />
              <option name="externalLinterArguments" value="--no-default-features" />
              <option name="macroExpansionEngine" value="DISABLED" />
              <option name="runExternalLinterOnTheFly" value="true" />
              <option name="runRustfmtOnSave" value="true" />
              <option name="showTestToolWindow" value="false" />
              <option name="toolchainHomeDirectory" value="/" />
              <option name="useOffline" value="true" />
              <option name="useSkipChildren" value="true" />
              <option name="version" value="2" />
            </RustProjectSettings>
        """.trimIndent()
        service.loadState(elementFromXmlString(text))

        val actual = service.state.toXmlString()
        assertEquals(text, actual)

        assertEquals(XML_FORMAT_VERSION, service.version)
        assertEquals(RustToolchain(Paths.get("/")), service.toolchain)
        assertEquals(false, service.autoUpdateEnabled)
        assertEquals(ExternalLinter.CLIPPY, service.externalLinter)
        assertEquals("/stdlib", service.explicitPathToStdlib)
        assertEquals(true, service.runExternalLinterOnTheFly)
        assertEquals("--no-default-features", service.externalLinterArguments)
        assertEquals(false, service.compileAllTargets)
        assertEquals(true, service.useOffline)
        assertEquals(MacroExpansionEngine.DISABLED, service.macroExpansionEngine)
        assertEquals(false, service.showTestToolWindow)
        assertEquals(false, service.doctestInjectionEnabled)
        assertEquals(true, service.runRustfmtOnSave)
        assertEquals(true, service.useSkipChildren)
        assertEquals(RustProjectSettingsService.FeaturesSetting.NoDefault, service.packagesSettings.cargoFeatures)
        assertEquals(setOf("foo", "bar"), service.packagesSettings.cargoFeaturesAdditional)
    }

    fun `test update from version 1`() {
        val service = RustProjectSettingsServiceImpl(project)

        @Language("XML")
        val text = """
            <RustProjectSettings>
              <option name="autoUpdateEnabled" value="true" />
              <option name="compileAllTargets" value="false" />
              <option name="useCargoCheckAnnotator" value="true" />
              <option name="expandMacros" value="false" />
            </RustProjectSettings>
        """.trimIndent()
        service.loadState(elementFromXmlString(text))

        @Language("XML")
        val expected = """
            <RustProjectSettings>
              <option name="compileAllTargets" value="false" />
              <option name="macroExpansionEngine" value="DISABLED" />
              <option name="runExternalLinterOnTheFly" value="true" />
              <option name="version" value="2" />
            </RustProjectSettings>
        """.trimIndent()
        val actual = service.state.toXmlString()
        assertEquals(expected, actual)

        assertEquals(true, service.runExternalLinterOnTheFly)
        assertEquals("", service.externalLinterArguments)
        assertEquals(MacroExpansionEngine.DISABLED, service.macroExpansionEngine)
    }

    fun `test defaults`() {
        val service = RustProjectSettingsServiceImpl(LightPlatformTestCase.getProject())
        @Language("XML")
        val text = """
            <State>
            </State>
        """.trimIndent()
        service.loadState(elementFromXmlString(text))

        assertEquals(XML_FORMAT_VERSION, service.version)
        assertEquals(null, service.toolchain)
        assertEquals(true, service.autoUpdateEnabled)
        assertEquals(ExternalLinter.CARGO_CHECK, service.externalLinter)
        assertEquals(null, service.explicitPathToStdlib)
        assertEquals(false, service.runExternalLinterOnTheFly)
        assertEquals("", service.externalLinterArguments)
        assertEquals(true, service.compileAllTargets)
        assertEquals(false, service.useOffline)
        assertEquals(MacroExpansionEngine.OLD, service.macroExpansionEngine)
        assertEquals(true, service.showTestToolWindow)
        assertEquals(true, service.doctestInjectionEnabled)
        assertEquals(false, service.runRustfmtOnSave)
        assertEquals(false, service.useSkipChildren)
        assertEquals(RustProjectSettingsService.FeaturesSetting.Default, service.packagesSettings.cargoFeatures)
        assertEquals(setOf<String>(), service.packagesSettings.cargoFeaturesAdditional)
    }
}
