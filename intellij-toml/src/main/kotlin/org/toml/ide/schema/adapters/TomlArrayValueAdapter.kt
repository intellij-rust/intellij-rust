/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema.adapters

import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.toml.lang.psi.TomlArray

class TomlArrayValueAdapter(element: TomlArray) : TomlValueAdapter<TomlArray>(element), JsonArrayValueAdapter {

    override fun isStringLiteral(): Boolean = false
    override fun isBooleanLiteral(): Boolean = false
    override fun isNumberLiteral(): Boolean = false

    override fun isObject(): Boolean = false
    override fun isArray(): Boolean = true

    override fun getAsArray(): JsonArrayValueAdapter? = this
    override fun getAsObject(): JsonObjectValueAdapter? = null
    override fun getElements(): List<JsonValueAdapter> = element.elements.map(::createAdapter)
}
