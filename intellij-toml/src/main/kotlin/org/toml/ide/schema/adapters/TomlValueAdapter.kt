/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema.adapters

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.toml.lang.psi.*

abstract class TomlValueAdapter<T : TomlElement>(protected val element: T) : JsonValueAdapter {

    override fun getDelegate(): PsiElement = element

    companion object {
        fun createAdapter(element: TomlValue): TomlValueAdapter<*> {
            return when (element) {
                is TomlArray -> TomlArrayValueAdapter(element)
                is TomlInlineTable -> TomlObjectValueAdapter(element)
                is TomlLiteral -> TomlLiteralValueAdapter(element)
                else -> error("Unexpected element: `${element.text}`")
            }
        }
    }
}
