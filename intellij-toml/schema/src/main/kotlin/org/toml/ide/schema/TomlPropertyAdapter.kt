/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlElement
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner

class TomlPropertyAdapter(val tomlKeyValue: TomlKeyValue) : JsonPropertyAdapter {
    override fun getName(): String = tomlKeyValue.key.text

    override fun getNameValueAdapter(): JsonValueAdapter = TomlGenericValueAdapter(tomlKeyValue.key)

    override fun getValues(): Collection<JsonValueAdapter> {
        val value = tomlKeyValue.value ?: return emptyList()
        return listOf(createAdapterByType(value))
    }

    override fun getDelegate(): PsiElement = tomlKeyValue

    override fun getParentObject(): JsonObjectValueAdapter? {
        val parent = tomlKeyValue.parent
        if (parent !is TomlKeyValueOwner) return null

        return TomlObjectAdapter(parent)
    }

    companion object {
        fun createAdapterByType(value: TomlElement) = when (value) {
                is TomlKeyValueOwner -> TomlObjectAdapter(value)
                is TomlArray -> TomlArrayAdapter(value)
                else -> TomlGenericValueAdapter(value)
            }
    }
}
