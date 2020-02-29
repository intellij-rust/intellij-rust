/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema.adapters

import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

class TomlLiteralValueAdapter(element: TomlLiteral) : TomlValueAdapter<TomlLiteral>(element) {
    override fun isNull(): Boolean = false

    override fun isBooleanLiteral(): Boolean = element.kind is TomlLiteralKind.Boolean
    override fun isNumberLiteral(): Boolean = element.kind is TomlLiteralKind.Number
    override fun isStringLiteral(): Boolean = element.kind is TomlLiteralKind.String

    override fun getAsArray(): JsonArrayValueAdapter? = null
    override fun getAsObject(): JsonObjectValueAdapter? = null

    override fun isObject(): Boolean = false
    override fun isArray(): Boolean = false
}
