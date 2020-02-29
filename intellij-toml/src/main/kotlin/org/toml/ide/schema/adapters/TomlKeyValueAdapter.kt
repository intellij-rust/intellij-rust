/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema.adapters

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner

class TomlKeyValueAdapter(private val element: TomlKeyValue) : JsonPropertyAdapter {
    override fun getValues(): Collection<JsonValueAdapter> {
        val value = element.value ?: return emptyList()
        return listOf(TomlValueAdapter.createAdapter(value))
    }

    override fun getNameValueAdapter(): JsonValueAdapter? = TomlPropertyKeyAdapter(element.key)

    // TODO: support quoted and dot keys
    override fun getName(): String? = element.key.text

    override fun getDelegate(): PsiElement = element

    override fun getParentObject(): JsonObjectValueAdapter? {
        val keyValueOwner = element.parent as? TomlKeyValueOwner ?: return null
        return TomlObjectValueAdapter(keyValueOwner)
    }
}
