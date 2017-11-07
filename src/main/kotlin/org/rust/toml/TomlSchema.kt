/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.intellij.lang.annotations.Language
import org.toml.lang.psi.TomlArrayTable
import org.toml.lang.psi.TomlFileType
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlTable

class TomlSchema private constructor(
    private val tables: List<TomlTableSchema>
) {

    fun topLevelKeys(isArray: Boolean): Collection<String> =
        tables.filter { it.isArray == isArray }.map { it.name }

    fun keysForTable(tableName: String): Collection<String> =
        tables.find { it.name == tableName }?.keys.orEmpty()

    companion object {
        fun parse(project: Project, @Language("TOML") example: String): TomlSchema {
            val toml = PsiFileFactory.getInstance(project)
                .createFileFromText("Cargo.toml", TomlFileType, example)

            val tables = toml.children
                .filterIsInstance<TomlKeyValueOwner>()
                .mapNotNull { it.schema }

            return TomlSchema(tables)
        }
    }
}

private val TomlKeyValueOwner.schema: TomlTableSchema?
    get() {
        val (name, isArray) = when (this) {
            is TomlTable -> header.names.firstOrNull()?.text to false
            is TomlArrayTable -> header.names.firstOrNull()?.text to true
            else -> return null
        }
        if (name == null) return null

        val keys = entries.mapNotNull { it.key.text }.filter { it != "foo" }
        return TomlTableSchema(name, isArray, keys)
    }

private class TomlTableSchema(
    val name: String,
    val isArray: Boolean,
    val keys: Collection<String>
)
