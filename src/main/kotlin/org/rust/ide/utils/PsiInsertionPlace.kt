/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import com.intellij.psi.PsiElement
import org.rust.ide.intentions.util.macros.IntentionInMacroUtil
import org.rust.lang.core.macros.RangeMap
import org.rust.lang.core.macros.findMacroCallAndOffsetExpandedFromUnchecked
import org.rust.lang.core.macros.findMacroCallExpandedFromNonRecursive
import org.rust.lang.core.macros.isExpandedFromMacro
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.ext.*

/**
 * A PSI modification utility indented to be used in [com.intellij.codeInsight.intention.IntentionAction] and
 * [com.intellij.codeInspection.LocalQuickFix] implementation. The advantage of this class is that it checks
 * that a target insertion place is writeable and in some cases can slightly *adjust* the insertion place
 * to make it possible to write there.
 */
sealed interface PsiInsertionPlace {
    fun <T: PsiElement> insert(psiToInsert: T): T
    fun <T: PsiElement> insertMultiple(psiToInsert: List<T>): List<T>
    fun <T: PsiElement> insertMultiple(vararg psiToInsert: T): List<T> =
        insertMultiple(psiToInsert.toList())

    private class Before(private val anchor: PsiElement) : PsiInsertionPlace {
        override fun <T: PsiElement> insert(psiToInsert: T): T {
            @Suppress("UNCHECKED_CAST")
            return anchor.parent.addBefore(psiToInsert, anchor) as T
        }

        override fun <T : PsiElement> insertMultiple(psiToInsert: List<T>): List<T> {
            return psiToInsert.map { insert(it) }
        }

        override fun toString(): String = "PsiInsertionPlace.Before(anchor = `${anchor.text}`)"
    }

    private class After(private val anchor: PsiElement) : PsiInsertionPlace {
        override fun <T: PsiElement> insert(psiToInsert: T): T {
            @Suppress("UNCHECKED_CAST")
            return anchor.parent.addAfter(psiToInsert, anchor) as T
        }

        override fun <T : PsiElement> insertMultiple(psiToInsert: List<T>): List<T> {
            return psiToInsert.asReversed().map { insert(it) }.reversed()
        }
        override fun toString(): String = "PsiInsertionPlace.After(anchor = `${anchor.text}`)"
    }

    private class Inside(private val parent: PsiElement) : PsiInsertionPlace {
        override fun <T: PsiElement> insert(psiToInsert: T): T {
            @Suppress("UNCHECKED_CAST")
            return parent.add(psiToInsert) as T
        }

        override fun <T : PsiElement> insertMultiple(psiToInsert: List<T>): List<T> {
            return psiToInsert.map { insert(it) }
        }
        override fun toString(): String = "PsiInsertionPlace.Inside(parent = `${parent.text}`)"
    }

    companion object {
        fun after(anchor: PsiElement): PsiInsertionPlace? {
            if (isEditableAt(anchor, anchor.endOffset)) {
                return After(anchor)
            }
            val nextSibling = anchor.getNextNonCommentSibling()
            if (nextSibling != null && isEditableAt(nextSibling, nextSibling.startOffset)) {
                return Before(nextSibling)
            }
            return null
        }

        fun before(anchor: PsiElement): PsiInsertionPlace? {
            if (isEditableAt(anchor, anchor.startOffset)) {
                return Before(anchor)
            }
            val prevSibling = anchor.getPrevNonCommentSibling()
            if (prevSibling != null && isEditableAt(prevSibling, prevSibling.endOffset)) {
                return After(prevSibling)
            }
            return null
        }

        fun afterLastChildIn(parent: PsiElement): PsiInsertionPlace? {
            val anchor = parent.lastChild
            return when {
                anchor != null -> after(anchor)
                isEditableAt(parent, parent.startOffset) -> Inside(parent)
                else -> null
            }
        }

        /**
         * If [context] is located in the same [mod], prefer location right *before* the [context].
         * Otherwise, insert somewhere in the [mod].
         */
        fun forItemInModBefore(mod: RsMod, context: RsElement): PsiInsertionPlace? {
            return if (mod == context.containingMod) {
                forItemBefore(context)
            } else {
                forItemInMod(mod)
            }
        }

        /**
         * If [context] is located in the same [mod], prefer location right *after* the [context].
         * Otherwise, insert somewhere in the [mod].
         */
        fun forItemInModAfter(mod: RsMod, context: RsElement): PsiInsertionPlace? {
            return if (mod == context.containingMod) {
                forItemAfter(context)
            } else {
                forItemInMod(mod)
            }
        }

        private fun forItemInMod(mod: RsMod): PsiInsertionPlace? {
            return if (mod is RsModItem) {
                val rbrace = mod.rbrace
                if (rbrace != null) {
                    before(rbrace)
                } else {
                    afterLastChildIn(mod)
                }
            } else {
                afterLastChildIn(mod)
            }
        }

        fun forItemBefore(context: RsElement): PsiInsertionPlace? {
            val topLevelItem = context.contexts.firstOrNull {
                it is RsItemElement && (it !is RsAbstractable || it.owner is RsAbstractableOwner.Free)
            } ?: return null

            val containingMod = context.containingMod
            return before(if (containingMod.containingFile == topLevelItem.containingFile) {
                topLevelItem
            } else {
                // Here, the `context` element is inside a macro expansion
                topLevelItem.climbUpThroughMacrosWhile { it.startOffset == 0 }
            })
        }

        fun forItemAfter(context: RsElement): PsiInsertionPlace? {
            val nearestItem = context.contexts.firstOrNull {
                it is RsItemElement && (it !is RsAbstractable || it.owner is RsAbstractableOwner.Free)
            } ?: return null

            val containingMod = context.containingMod
            return after(if (containingMod.containingFile == nearestItem.containingFile) {
                nearestItem
            } else {
                // Here, the `context` element is inside a macro expansion
                nearestItem.climbUpThroughMacrosWhile { it.endOffset == it.containingFile.endOffset }
            })
        }

        /**
         * Use it if you want to insert something that refers to [context]
         */
        fun forItemInTheScopeOf(context: RsElement): PsiInsertionPlace? {
            val currentScope = context.contexts.firstOrNull { it.context is RsItemsOwner }
                ?: return null

            val containingMod = context.containingMod
            return after(if (containingMod.containingFile == currentScope.containingFile) {
                currentScope
            } else {
                // Here, the `context` element is inside a macro expansion
                currentScope.climbUpThroughMacrosWhile { it.endOffset == it.containingFile.endOffset }
            })
        }

        private fun PsiElement.climbUpThroughMacrosWhile(condition: (PsiElement) -> Boolean): PsiElement {
            var element = this
            while (condition(element)) {
                element = element.findMacroCallExpandedFromNonRecursive()
                    ?.let { if (it is RsMetaItem) it.owner else it }
                    ?: break
            }
            return element
        }

        fun forTraitOrImplMember(traitOrImpl: RsTraitOrImpl): PsiInsertionPlace? {
            val members = traitOrImpl.members ?: return null
            val rbrace = members.rbrace
            return if (rbrace != null) {
                before(rbrace)
            } else {
                afterLastChildIn(members)
            }
        }

        private fun isEditableAt(element: PsiElement, absoluteOffsetInFile: Int): Boolean {
            if (!element.isExpandedFromMacro) return PsiModificationUtil.isWriteableRegardlessMacros(element)
            if (!IntentionInMacroUtil.isMutableExpansionFile(element.containingFile)) return false

            val (call, _) = findMacroCallAndOffsetExpandedFromUnchecked(
                element,
                absoluteOffsetInFile,
                RangeMap.StickTo.ANY
            ) ?: return false

            if (call.isExpandedFromMacro) return false

            return PsiModificationUtil.isWriteableRegardlessMacros(call)
        }
    }
}
