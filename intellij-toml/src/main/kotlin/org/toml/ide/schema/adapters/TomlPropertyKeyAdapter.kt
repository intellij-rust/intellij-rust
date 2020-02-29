/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema.adapters

import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import org.toml.lang.psi.TomlKey

class TomlPropertyKeyAdapter(element: TomlKey) : TomlValueAdapter<TomlKey>(element) {
    override fun isNull(): Boolean = false

    override fun isBooleanLiteral(): Boolean = false
    override fun isNumberLiteral(): Boolean = false
    override fun isStringLiteral(): Boolean = false

    override fun isObject(): Boolean  = false
    override fun isArray(): Boolean = false

    override fun getAsObject(): JsonObjectValueAdapter? = null
    override fun getAsArray(): JsonArrayValueAdapter? = null
}
