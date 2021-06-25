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
        if (element is TomlKeySegment) return ThreeState.YES
        return ThreeState.NO
    }

    override fun isPropertyWithValue(element: PsiElement): Boolean =
        element is TomlKeyValue && element.value != null

    override fun findElementToCheck(element: PsiElement): PsiElement? {
        var current: PsiElement? = element

        while (current != null && current !is PsiFile) {
            if (current is TomlElement) return current
            current = current.parent
        }

        return null
    }

    override fun findPosition(element: PsiElement, forceLastTransition: Boolean): JsonPointerPosition {
        val position = JsonPointerPosition()
        var current = element

        while (current !is PsiFile) {
            when {
                current is TomlKeyValue -> {
                    val key = current.key

                    for (segment in key.segments) {
                        if (segment != element || forceLastTransition) {
                            position.addPrecedingStep(segment.name)
                        }
                    }
                }
                current is TomlKeyValueOwner && current is TomlHeaderOwner -> {
                    val key = current.header.key ?: break

                    for (segment in key.segments) {
                        if (segment != element || forceLastTransition) {
                            position.addPrecedingStep(segment.name)
                        }
                    }
                }
            }

            current = current.parent
        }

        return position
    }

    override fun getPropertyNamesOfParentObject(originalPosition: PsiElement, computedPosition: PsiElement?): Set<String> {
        val table = originalPosition.parentOfType<TomlTable>()
            ?: originalPosition.parentOfType<TomlInlineTable>()
            ?: originalPosition.prevSibling as? TomlTable
            ?: return emptySet()
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

    override fun getNodeTextForValidation(element: PsiElement?): String {
        return super.getNodeTextForValidation(element)
    }

    override fun hasMissingCommaAfter(element: PsiElement): Boolean = false

    override fun acceptsEmptyRoot(): Boolean = true
    override fun requiresNameQuotes(): Boolean = false
    override fun allowsSingleQuotes(): Boolean = false

    companion object {
        val INSTANCE = TomlJsonPsiWalker()
    }
}
