/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

/**
 * Flatten imports 1 depth
 *
 * ```
 * use a::{
 *   b::foo,
 *   b::bar
 * }
 * ```
 *
 * to this:
 *
 * ```
 * use a::b::foo;
 * use a::b::bar;
 * ```
 */
class FlattenUseStatementsIntention : RsElementBaseIntentionAction<FlattenUseStatementsIntention.Context>() {
    override fun getText() = "Flatten use statements"
    override fun getFamilyName() = text

    interface Context {
        val useSpecks: List<RsUseSpeck>
        val root: PsiElement
        val firstOldElement: PsiElement
        fun createElements(paths: List<String>, project: Project): List<PsiElement>
        val oldElements: List<PsiElement>
        val cursorOffset: Int
        val basePath: String
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val useGroupOnCursor = element.ancestorStrict<RsUseGroup>() ?: return null
        val isNested = useGroupOnCursor.ancestorStrict<RsUseGroup>() != null
        val useSpeckOnCursor = element.ancestorStrict<RsUseSpeck>() ?: return null

        return if (isNested) {
            PathInNestedGroup.create(useGroupOnCursor, useSpeckOnCursor)
        } else {
            PathInGroup.create(useGroupOnCursor, useSpeckOnCursor)
        }
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val paths = makeSeparatedPath(ctx.basePath, ctx.useSpecks).let {
            ctx.createElements(it, project)
        }.map {
            ctx.root.addBefore(it, ctx.firstOldElement)
        }

        for (elem in ctx.oldElements) {
            elem.delete()
        }

        val nextUseSpeckExists = (paths.lastOrNull()?.rightSiblings?.filterIsInstance<RsUseSpeck>()?.count() ?: 0) > 0
        if (!nextUseSpeckExists) {
            paths.last().rightSiblings.find { it.text == "\n" }?.delete()
        }

        editor.caretModel.moveToOffset((paths.firstOrNull()?.startOffset ?: 0) + ctx.cursorOffset)
    }

    private fun makeSeparatedPath(basePath: String, useSpecks: List<RsUseSpeck>): List<String> = useSpecks.flatMap {
        it.useGroup?.useSpeckList?.map { "$basePath::${it.text}" } ?: listOf(addBasePath(it.text, basePath))
    }

    private fun addBasePath(localPath: String, basePath: String): String = when (localPath) {
        "self" -> basePath
        else -> "$basePath::$localPath"
    }


    class PathInNestedGroup(
        useGroup: RsUseGroup,
        override val useSpecks: List<RsUseSpeck>,
        override val basePath: String
    ) : Context {

        companion object {
            fun create(useGroup: RsUseGroup, useSpeckOnCursor: RsUseSpeck): PathInNestedGroup? {
                val useSpeckList = mutableListOf<RsUseSpeck>()

                val basePath = useGroup.parentUseSpeck.path?.text ?: return null
                useSpeckList += useSpeckOnCursor.leftSiblings.filterIsInstance<RsUseSpeck>()
                useSpeckList += useSpeckOnCursor
                useSpeckList += useSpeckOnCursor.rightSiblings.filterIsInstance<RsUseSpeck>()
                if (useSpeckList.size == 1) return null

                return PathInNestedGroup(useGroup, useSpeckList, basePath)
            }
        }

        override fun createElements(paths: List<String>, project: Project): List<PsiElement> =
            RsPsiFactory(project).let { psiFactory ->
                paths.map {
                    psiFactory.createUseSpeck(it).also { useSpeck ->
                        useSpeck.add(psiFactory.createComma())
                        useSpeck.add(psiFactory.createNewline())
                    }
                }
            }

        override val firstOldElement: PsiElement = useGroup.parent

        override val oldElements: List<PsiElement> = listOf(firstOldElement).flatMap {
            if (it.nextSibling?.elementType == RsElementTypes.COMMA) {
                listOf(it, it.nextSibling)
            } else {
                listOf(it)
            }
        }

        override val root = useGroup.parent?.parent ?: throw IllegalStateException()

        override val cursorOffset: Int = 0
    }

    class PathInGroup(
        useGroup: RsUseGroup,
        override val useSpecks: List<RsUseSpeck>,
        override val basePath: String
    ) : Context {

        companion object {
            fun create(useGroup: RsUseGroup, useSpeckOnCursor: RsUseSpeck): PathInGroup? {
                val useSpeckList = mutableListOf<RsUseSpeck>()

                val basePath = useGroup.parentUseSpeck.path?.text ?: return null
                useSpeckList += useSpeckOnCursor.leftSiblings.filterIsInstance<RsUseSpeck>()
                useSpeckList += useSpeckOnCursor
                useSpeckList += useSpeckOnCursor.rightSiblings.filterIsInstance<RsUseSpeck>()
                if (useSpeckList.size == 1) return null

                return PathInGroup(useGroup, useSpeckList, basePath)
            }
        }

        override fun createElements(paths: List<String>, project: Project): List<PsiElement> =
            RsPsiFactory(project).let { psiFactory ->
                // Visibility modifier and attributes are check for only here because it is invalid to have a visibility
                // modifier inside a use group, same with attributes. For e.g. this is invalid:
                // use std::{
                //     pub io,
                //     #[cfg(test)] error
                // }
                paths.map {
                    // In case the group `use` had a visibility modifier remember to add it back.
                    // E.g.: pub use x::{y, z} -> pub use x::y; pub use x::z;
                    val item = psiFactory.createUseItem(it, visibility ?: "")
                    // In case the group `use` had attributes remember to add them back.
                    // E.g. #[a] #[b] use x::{y, z} -> #[a] #[b] use x::y; #[a] #[b] use x::z;
                    attrs.reversed().forEach { attr ->
                        // reversed() makes it so that the attributes are in the same order, otherwise they would've
                        // been in reverse
                        val attrPsi = psiFactory.createOuterAttr(attr.metaItem.text)
                        item.addBefore(attrPsi, item.firstChild)
                    }

                    item
                }
            }

        override val firstOldElement: PsiElement = useGroup.parent?.parent ?: throw IllegalStateException()

        private val attrs: Collection<RsAttr> = firstOldElement.descendantsOfType()

        private val visibility: String? = (firstOldElement as? RsUseItem)?.vis?.text

        override val oldElements: List<PsiElement> = listOf(firstOldElement)

        override val root = useGroup.parent?.parent?.parent ?: throw IllegalStateException()

        override val cursorOffset: Int = "use ".length +
            (if (visibility != null) visibility.length + 1 else 0) +
            (attrs.sumBy { it.textLength })
    }
}
