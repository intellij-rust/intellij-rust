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

object TomlJsonPsiWalker : JsonLikePsiWalker {
    override fun isName(element: PsiElement?): ThreeState =
        if (element is TomlKeySegment) ThreeState.YES else ThreeState.NO

    override fun isPropertyWithValue(element: PsiElement): Boolean =
        element is TomlKeyValue && element.value != null

    override fun findElementToCheck(element: PsiElement): PsiElement? =
        element.parentOfType<TomlElement>()

    override fun findPosition(element: PsiElement, forceLastTransition: Boolean): JsonPointerPosition {
        val position = JsonPointerPosition()
        var current = element

        while (current !is PsiFile) {
            val parent = current.parent
            when {
                parent is TomlArray -> {
                    val entries = parent.elements

                    var currentIndex = -1
                    for ((idx, entry) in entries.withIndex()) {
                        if (current == entry) {
                            currentIndex = idx
                            break
                        }
                    }
                    position.addPrecedingStep(currentIndex)
                }
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
        return TomlJsonObjectAdapter(table).propertyList.mapNotNull { it.name }.toSet()
    }

    override fun getParentPropertyAdapter(element: PsiElement): JsonPropertyAdapter? {
        val property = element.parentOfType<TomlKeyValue>(true) ?: return null
        return TomlJsonPropertyAdapter(property)
    }

    override fun isTopJsonElement(element: PsiElement): Boolean = element is TomlFile

    override fun createValueAdapter(element: PsiElement): JsonValueAdapter? =
        if (element is TomlElement) TomlJsonValueAdapter.createAdapterByType(element) else null

    override fun getRoots(file: PsiFile): Collection<PsiElement> {
        if (file !is TomlFile) return emptyList()
        return file.children.toHashSet()
    }

    override fun getPropertyNameElement(property: PsiElement?): PsiElement? = (property as? TomlKeyValue)?.key

    override fun hasMissingCommaAfter(element: PsiElement): Boolean = false

    override fun acceptsEmptyRoot(): Boolean = true
    override fun requiresNameQuotes(): Boolean = false
    override fun allowsSingleQuotes(): Boolean = false
}
