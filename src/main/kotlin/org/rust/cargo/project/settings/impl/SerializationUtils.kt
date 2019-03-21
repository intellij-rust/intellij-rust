/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:JvmName("SerializationUtils")
@file:Suppress("unused")

package org.rust.cargo.project.settings.impl

import com.intellij.util.addOptionTag
import com.intellij.util.xmlb.Constants
import org.jdom.Element
import org.rust.cargo.project.settings.RustProjectSettingsService.MacroExpansionEngine

// Bump this number if Rust project settings changes
const val XML_FORMAT_VERSION: Int = 2

const val VERSION: String = "version"
const val TOOLCHAIN_HOME_DIRECTORY: String = "toolchainHomeDirectory"
const val AUTO_UPDATE_ENABLED: String = "autoUpdateEnabled"
const val EXPLICIT_PATH_TO_STDLIB: String = "explicitPathToStdlib"
const val EXTERNAL_LINTER: String = "externalLinter"
const val RUN_EXTERNAL_LINTER_ON_THE_FLY: String = "runExternalLinterOnTheFly"
const val EXTERNAL_LINTER_ARGUMENTS: String = "externalLinterArguments"
const val COMPILE_ALL_TARGETS: String = "compileAllTargets"
const val USE_OFFLINE: String = "useOffline"
const val MACRO_EXPANSION_ENGINE: String = "macroExpansionEngine"
const val SHOW_TEST_TOOL_WINDOW: String = "showTestToolWindow"
const val DOCTEST_INJECTION_ENABLED: String = "doctestInjectionEnabled"
const val RUN_RUSTFMT_ON_SAVE: String = "runRustfmtOnSave"
const val USE_SKIP_CHILDREN: String = "useSkipChildren"

// Legacy properties needed for migration
const val USE_CARGO_CHECK_ANNOTATOR: String = "useCargoCheckAnnotator"
const val CARGO_CHECK_ARGUMENTS: String = "cargoCheckArguments"
const val EXPAND_MACROS: String = "expandMacros"

fun Element.updateToCurrentVersion() {
    updateToVersionIfNeeded(2) {
        renameOption(USE_CARGO_CHECK_ANNOTATOR, RUN_EXTERNAL_LINTER_ON_THE_FLY)
        renameOption(CARGO_CHECK_ARGUMENTS, EXTERNAL_LINTER_ARGUMENTS)
        if (getOptionValueAsBoolean(EXPAND_MACROS) == false) {
            setOptionValue(MACRO_EXPANSION_ENGINE, MacroExpansionEngine.DISABLED)
        }
    }
    check(version == XML_FORMAT_VERSION)
}

private fun Element.updateToVersionIfNeeded(newVersion: Int, update: Element.() -> Unit) {
    if (version != newVersion - 1) return
    update()
    setOptionValue(VERSION, newVersion)
}

private val Element.version: Int get() = getOptionValueAsInt(VERSION) ?: 1

private fun Element.getOptionWithName(name: String): Element? =
    children.find { it.getAttribute(Constants.NAME)?.value == name }

private fun Element.getOptionValue(name: String): String? =
    getOptionWithName(name)?.getAttributeValue(Constants.VALUE)

private fun Element.getOptionValueAsBoolean(name: String): Boolean? =
    getOptionValue(name)?.let {
        when (it) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

private fun Element.getOptionValueAsInt(name: String): Int? =
    getOptionValue(name)?.let { Integer.valueOf(it) }

private fun Element.setOptionValue(name: String, value: Any) {
    val option = getOptionWithName(name)
    if (option != null) {
        option.setAttribute(Constants.VALUE, value.toString())
    } else {
        addOptionTag(name, value.toString())
    }
}

private fun Element.renameOption(oldName: String, newName: String) {
    getOptionWithName(oldName)?.setAttribute(Constants.NAME, newName)
}
