/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import org.rust.ide.icons.RsIcons
import javax.swing.Icon

sealed class RsProjectTemplate(val name: String, val isBinary: Boolean) {
    abstract val icon: Icon

    fun validateProjectName(crateName: String): String? = RsPackageNameValidator.validate(crateName, isBinary)
}

class RsGenericTemplate(name: String, isBinary: Boolean) : RsProjectTemplate(name, isBinary) {
    override val icon: Icon = RsIcons.RUST
}

class RsCustomTemplate(name: String, val link: String, isBinary: Boolean = true) : RsProjectTemplate(name, isBinary) {
    override val icon: Icon = RsIcons.CARGO_GENERATE
    val shortLink: String
        get() = link.substringAfter("//")
}
