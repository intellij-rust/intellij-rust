/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.toml.ide.schema.adapters.TomlKeyValueAdapter
import org.toml.ide.schema.adapters.TomlSyntaxAdapter
import org.toml.ide.schema.adapters.TomlValueAdapter
import org.toml.lang.psi.*

object TomlJsonLikePsiWalker : JsonLikePsiWalker {
    override fun allowsSingleQuotes(): Boolean = true
    override fun requiresNameQuotes(): Boolean = false

    override fun getPropertyNamesOfParentObject(originalPosition: PsiElement, computedPosition: PsiElement?): Set<String> {
        val keyValueOwner = originalPosition.parentOfType<TomlKeyValueOwner>() ?: return emptySet()
        // TODO: support quoted and dotted keys
        return keyValueOwner.entries.mapTo(HashSet()) { it.key.text }
    }

    override fun getParentPropertyAdapter(element: PsiElement): JsonPropertyAdapter? {
        val keyValue = element.parentOfType<TomlKeyValue>() ?: return null
        return TomlKeyValueAdapter(keyValue)
    }

    override fun createValueAdapter(element: PsiElement): JsonValueAdapter? {
        return if (element is TomlValue) TomlValueAdapter.createAdapter(element) else null
    }

    override fun findElementToCheck(element: PsiElement): PsiElement? {
        return PsiTreeUtil.getParentOfType(element, TomlValue::class.java, TomlKey::class.java, TomlKeyValue::class.java)
    }

    override fun findPosition(element: PsiElement, forceLastTransition: Boolean): JsonPointerPosition? {
        val pos = JsonPointerPosition()
        var current = element

        while (current !is TomlFile) {
            val position = current
            current = position.parent
            when (current) {
                is TomlKeyValue -> {
                    // key = < { } >  ->  < key = { } >
                    // TODO: support dotted keys. Maybe we need better grammar for them?
                    val key = current.key.text
                    if (CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED !in key) {
                        pos.addPrecedingStep(key)
                    }
                    current = current.parent
                }

                is TomlArray -> {
                    val idx = current.elements.indexOf(position as TomlValue)
                    pos.addPrecedingStep(idx)
                }
                is TomlInlineTable -> {
                    // TODO: support dotted keys. Maybe we need better grammar for them?
                    val key = (position as TomlKeyValue).key.text
                    pos.addPrecedingStep(key)

                }
                is TomlTable -> {
                    for (key in current.header.names.asReversed()) {
                        pos.addPrecedingStep(key.text)
                    }
                }
//                is TomlArrayTable -> {
//
//
//                }
            }
        }
        return pos
    }

    override fun getSyntaxAdapter(project: Project): JsonLikeSyntaxAdapter = TomlSyntaxAdapter(project)

    override fun hasMissingCommaAfter(element: PsiElement): Boolean = false

    override fun isPropertyWithValue(element: PsiElement): Boolean {
        return element is TomlKeyValue && element.value != null
    }

    override fun isTopJsonElement(element: PsiElement): Boolean = element is TomlFile

    override fun isName(element: PsiElement): ThreeState {
        return if (element is TomlKey) ThreeState.YES else ThreeState.NO
    }

    override fun getRoots(file: PsiFile): Collection<PsiElement> = listOfNotNull(file as? TomlFile)

    override fun getPropertyNameElement(property: PsiElement?): PsiElement? {
        return (property as? TomlKeyValue)?.key
    }
}
