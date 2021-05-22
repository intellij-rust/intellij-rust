/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.ext.getQueryAttributes
import org.rust.lang.core.psi.ext.name

/**
 * `#![register_attr()]` and `#![register_tool()]` crate-root attributes
 */
data class CustomAttributes(
    val customAttrs: Set<String>,
    val customTools: Set<String>,
) {
    companion object {
        val EMPTY: CustomAttributes = CustomAttributes(emptySet(), emptySet())

        fun fromCrate(crate: Crate): CustomAttributes {
            val rootMod = crate.rootMod ?: return EMPTY
            return fromRootModule(rootMod, crate)
        }

        fun fromRootModule(rootMod: RsFile, crate: Crate): CustomAttributes {
            val attrs = mutableSetOf<String>()
            val tools = mutableSetOf<String>()
            for (meta in rootMod.getQueryAttributes(crate).metaItems) {
                if (meta.name == "register_attr") {
                    for (attr in meta.metaItemArgs?.metaItemList.orEmpty()) {
                        attr.name?.let { attrs += it }
                    }
                }
                if (meta.name == "register_tool") {
                    for (tool in meta.metaItemArgs?.metaItemList.orEmpty()) {
                        tool.name?.let { tools += it }
                    }
                }
            }
            return CustomAttributes(attrs, tools)
        }
    }
}
