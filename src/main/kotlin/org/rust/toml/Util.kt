/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.notification.NotificationType
import org.rust.ide.notifications.showBalloonWithoutProject
import org.toml.lang.psi.*
import kotlin.reflect.KProperty


fun tomlPluginIsAbiCompatible(): Boolean = computeOnce

private val computeOnce: Boolean by lazy {
    try {
        load<TomlKey>()
        load<TomlValue>()

        load(TomlKeyValue::key)
        load(TomlKeyValue::value)

        load(TomlTable::entries)
        load(TomlTable::header)

        load(TomlArrayTable::entries)
        load(TomlArrayTable::header)

        load(TomlTableHeader::names)
        load(TomlKeyValueOwner::entries)
        true
    } catch (e: LinkageError) {
        showBalloonWithoutProject(
            "Incompatible TOML plugin version, code completion for Cargo.toml is not available.",
            NotificationType.WARNING
        )
        false
    }
}

private inline fun <reified T : Any> load(): String = T::class.java.name
private fun load(p: KProperty<*>): String = p.name
