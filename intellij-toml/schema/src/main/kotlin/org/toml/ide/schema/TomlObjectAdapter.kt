/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import org.toml.lang.psi.TomlKeyValueOwner

class TomlObjectAdapter(val tomlTableObject: TomlKeyValueOwner) : JsonObjectValueAdapter {
    private val childAdapters by lazy {
        tomlTableObject.entries.map { TomlPropertyAdapter(it) }
    }

    override fun getPropertyList(): List<JsonPropertyAdapter> = childAdapters

    override fun isObject(): Boolean = true
    override fun isArray(): Boolean = false
    override fun isStringLiteral(): Boolean = false
    override fun isNumberLiteral(): Boolean = false
    override fun isBooleanLiteral(): Boolean = false

    override fun getDelegate(): PsiElement = tomlTableObject
    override fun getAsObject(): JsonObjectValueAdapter = this
    override fun getAsArray(): JsonArrayValueAdapter? = null

}
