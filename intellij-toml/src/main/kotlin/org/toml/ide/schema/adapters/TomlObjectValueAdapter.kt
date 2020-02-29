/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema.adapters

import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import org.toml.lang.psi.TomlKeyValueOwner

class TomlObjectValueAdapter(element: TomlKeyValueOwner) : TomlValueAdapter<TomlKeyValueOwner>(element), JsonObjectValueAdapter {

    override fun isStringLiteral(): Boolean = false
    override fun isBooleanLiteral(): Boolean = false
    override fun isNumberLiteral(): Boolean = false

    override fun isObject(): Boolean = true
    override fun isArray(): Boolean = false

    override fun getAsArray(): JsonArrayValueAdapter? = null
    override fun getAsObject(): JsonObjectValueAdapter? = this

    override fun getPropertyList(): List<JsonPropertyAdapter> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
