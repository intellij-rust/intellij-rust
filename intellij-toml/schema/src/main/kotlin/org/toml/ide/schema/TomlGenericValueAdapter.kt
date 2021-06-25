/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.toml.lang.psi.TomlElement
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

class TomlGenericValueAdapter(val value: TomlElement) : JsonValueAdapter {
    override fun isObject(): Boolean = false
    override fun isArray(): Boolean = false
    override fun isNull(): Boolean = false

    override fun isStringLiteral(): Boolean =
        value is TomlLiteral && value.kind is TomlLiteralKind.String || value is TomlKey

    override fun isNumberLiteral(): Boolean = value is TomlLiteral && value.kind is TomlLiteralKind.Number
    override fun isBooleanLiteral(): Boolean = value is TomlLiteral && value.kind is TomlLiteralKind.Boolean

    override fun getDelegate(): PsiElement = value

    override fun getAsObject(): JsonObjectValueAdapter? = null
    override fun getAsArray(): JsonArrayValueAdapter? = null
}
