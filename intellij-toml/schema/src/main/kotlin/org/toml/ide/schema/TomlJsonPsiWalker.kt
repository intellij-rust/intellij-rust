/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.toml.lang.psi.*

class TomlJsonPsiWalker private constructor() : JsonLikePsiWalker {
    override fun isName(element: PsiElement?): ThreeState {
        if (element is TomlKey || element is TomlKeyValue) return ThreeState.YES;
        return ThreeState.NO;
    }

    override fun isPropertyWithValue(element: PsiElement): Boolean = element is TomlKeyValue && element.value != null

    override fun findElementToCheck(element: PsiElement): PsiElement? {
        var current: PsiElement? = element

        while (current != null && current !is PsiFile) {
            if (current is TomlValue || current is TomlKeyValue) return current
            current = current.parent
        }

        return null
    }

    override fun findPosition(element: PsiElement, forceLastTransition: Boolean): JsonPointerPosition? {
        val pos = JsonPointerPosition()
        var current = element

        while (current !is PsiFile) {
            val position = current
            current = current.parent

            if (current is TomlArray) {
                var requiredIndex = -1

                for ((index, value) in current.elements.withIndex()) {
                    if (value == position) {
                        requiredIndex = index
                        break
                    }
                }

                pos.addPrecedingStep(requiredIndex)
            } else if (current is TomlKeyValue) {
                val propertyName = current.key.text

                if (current.parent !is TomlKeyValueOwner) return null // incorrect syntax?

                // if either value or not first in the chain - needed for completion variant
                if (position != element || forceLastTransition) {
                    pos.addPrecedingStep(propertyName)
                }
            } else if (current is TomlKeyValueOwner && current is TomlHeaderOwner) {
                // if either value or not first in the chain - needed for completion variant
//                if (position != element || forceLastTransition) {
                    val propertyName = current.header.key?.text ?: break
                    pos.addPrecedingStep(propertyName)
//                }
            } else return if (current is PsiFile) {
                break
            } else {
                null // something went wrong
            }
        }

        return pos
    }

    override fun requiresNameQuotes(): Boolean = false

    override fun allowsSingleQuotes(): Boolean = false

    override fun hasMissingCommaAfter(element: PsiElement): Boolean = false

    override fun getPropertyNamesOfParentObject(originalPosition: PsiElement, computedPosition: PsiElement?): Set<String> {
        val table = originalPosition.parentOfType<TomlTable>() ?: originalPosition.prevSibling as? TomlTable ?: return emptySet()
        return TomlObjectAdapter(table).propertyList.mapNotNull { it.name }.toSet()
    }

    override fun getParentPropertyAdapter(element: PsiElement): JsonPropertyAdapter? {
        val property = element.parentOfType<TomlKeyValue>(true) ?: return null
        return TomlPropertyAdapter(property)
    }

    override fun isTopJsonElement(element: PsiElement): Boolean = element is TomlFile

    override fun createValueAdapter(element: PsiElement): JsonValueAdapter? =
        if (element is TomlElement) TomlPropertyAdapter.createAdapterByType(element) else null

    override fun getRoots(file: PsiFile): Collection<PsiElement> {
        if (file !is TomlFile) return emptyList()

        val roots = hashSetOf<PsiElement>()
        roots.addAll(file.children)
        return roots
    }

    override fun getPropertyNameElement(property: PsiElement?): PsiElement? = (property as? TomlKeyValue)?.key

    override fun acceptsEmptyRoot(): Boolean = true

    override fun getDefaultArrayValue(): String = ""

    override fun getDefaultObjectValue(): String = ""

    override fun getNodeTextForValidation(element: PsiElement?): String {
        return super.getNodeTextForValidation(element)
    }

    companion object {
        val INSTANCE = TomlJsonPsiWalker()
    }
}
