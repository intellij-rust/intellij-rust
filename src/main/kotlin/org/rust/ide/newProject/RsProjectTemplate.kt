/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.openapi.util.NlsContexts.ListItem
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.ide.icons.RsIcons
import javax.swing.Icon

sealed class RsProjectTemplate(@Suppress("UnstableApiUsage") @ListItem val name: String, val isBinary: Boolean, val icon: Icon) {
    @Nls
    fun validateProjectName(crateName: String): String? = RsPackageNameValidator.validate(crateName, isBinary)
}

sealed class RsGenericTemplate(@Suppress("UnstableApiUsage") @ListItem name: String, isBinary: Boolean) : RsProjectTemplate(name, isBinary, RsIcons.RUST) {
    object CargoBinaryTemplate : RsGenericTemplate(RsBundle.message("list.item.binary.application"), true)
    object CargoLibraryTemplate : RsGenericTemplate(RsBundle.message("list.item.library"), false)
}

open class RsCustomTemplate(
    @Suppress("UnstableApiUsage") @ListItem name: String,
    val url: String
) : RsProjectTemplate(name, false, RsIcons.CARGO_GENERATE) {
    object ProcMacroTemplate : RsCustomTemplate(RsBundle.message("list.item.procedural.macro"), "https://github.com/intellij-rust/rust-procmacro-quickstart-template")
    object WasmPackTemplate : RsCustomTemplate(RsBundle.message("list.item.webassembly.lib"), "https://github.com/intellij-rust/wasm-pack-template")

    val shortLink: String
        get() = url.substringAfter("//")
}
