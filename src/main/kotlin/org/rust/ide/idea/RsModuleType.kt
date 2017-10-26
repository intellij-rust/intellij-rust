/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.idea

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import org.rust.ide.icons.RsIcons
import javax.swing.Icon

class RsModuleType : ModuleType<RsModuleBuilder>(ID) {
    override fun getNodeIcon(isOpened: Boolean): Icon = RsIcons.RUST

    override fun createModuleBuilder(): RsModuleBuilder = RsModuleBuilder()

    override fun getDescription(): String = "Rust module"

    override fun getName(): String = "Rust"

    companion object {
        private val ID = "RUST_MODULE"
        val INSTANCE: RsModuleType by lazy { ModuleTypeManager.getInstance().findByID(ID) as RsModuleType }
    }
}
