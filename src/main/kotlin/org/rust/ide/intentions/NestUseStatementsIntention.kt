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
 * Nest imports 1 depth
 *
 * ```
 * use a::b::foo;
 * use a::b::bar;
 * ```
 *
 * to this:
 *
 * ```
 * use a::{
 *   b::foo,
 *   b::bar
 * }
 * ```
 */
class NestUseStatementsIntention : RsElementBaseIntentionAction<NestUseStatementsIntention.Context>() {
    override fun getText() = "Nest use statements"
    override fun getFamilyName() = text

    interface Context {
        val useSpecks: List<RsUseSpeck>
        val root: PsiElement
        val firstOldElement: PsiElement
        fun createElement(path: String, project: Project): PsiElement
        val oldElements: List<PsiElement>
        val cursorOffset: Int
        val basePath: String
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val useItemOnCursor = element.ancestorStrict<RsUseItem>() ?: return null
        val useGroupOnCursor = element.ancestorStrict<RsUseGroup>()
        val useSpeckOnCursor = element.ancestorStrict<RsUseSpeck>() ?: return null

        return if (useGroupOnCursor != null) {
            PathInGroup.create(useGroupOnCursor, useSpeckOnCursor)
        } else {
            PathInUseItem.create(useItemOnCursor, useSpeckOnCursor)
        }
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val path = makeGroupedPath(ctx.basePath, ctx.useSpecks)

        val inserted = ctx.root.addAfter(ctx.createElement(path, project), ctx.firstOldElement)

        for (prevElement in ctx.oldElements) {
            prevElement.delete()
        }

        val nextUseSpeckExists = inserted.rightSiblings.filterIsInstance<RsUseSpeck>().count() > 0
        if (nextUseSpeckExists) {
            ctx.root.addAfter(RsPsiFactory(project).createComma(), inserted)
        }

        editor.caretModel.moveToOffset(inserted!!.startOffset + ctx.cursorOffset)
    }

    private fun makeGroupedPath(basePath: String, useSpecks: List<RsUseSpeck>): String {
        val useSpecksInGroup = useSpecks.flatMap {
            // Remove first group
            val useGroup = it.useGroup
            if (it.path?.referenceName == basePath && useGroup != null) {
                useGroup.useSpeckList.map { it.text }
            } else {
                listOf(deleteBasePath(it.text, basePath))
            }
        }
        return useSpecksInGroup.joinToString(",\n", "$basePath::{\n", "\n}")
    }

    private fun deleteBasePath(fullPath: String, basePath: String): String {
        return when {
            fullPath == basePath -> "self"
            fullPath.startsWith(basePath) -> fullPath.removePrefix("$basePath::")
            else -> fullPath
        }
    }

    class PathInUseItem(
        useItem: RsUseItem,
        useItems: List<RsUseItem>,
        override val basePath: String
    ) : Context {

        companion object {
            fun create(useItemOnCursor: RsUseItem, useSpeck: RsUseSpeck): PathInUseItem? {
                val useItemList = mutableListOf<RsUseItem>()

                val basePath = useSpeck.path?.let { getBasePathFromPath(it) } ?: return null
                useItemList += useItemOnCursor.leftSiblings.filterIsInstance<RsUseItem>()
                    .filter { it.useSpeck?.path?.let { getBasePathFromPath(it) } == basePath }
                useItemList.add(useItemOnCursor)
                useItemList += useItemOnCursor.rightSiblings.filterIsInstance<RsUseItem>()
                    .filter { it.useSpeck?.path?.let { getBasePathFromPath(it) } == basePath }
                if (useItemList.size == 1) return null

                return PathInUseItem(useItemOnCursor, useItemList, basePath)
            }
        }

        override fun createElement(path: String, project: Project): PsiElement {
            return RsPsiFactory(project).createUseItem(path)
        }

        override val useSpecks: List<RsUseSpeck> = useItems.mapNotNull { it.useSpeck }

        override val oldElements: List<PsiElement> = useItems

        override val firstOldElement: PsiElement = useItems.first()

        override val root: PsiElement = useItem.parent

        override val cursorOffset: Int = "use ".length
    }

    class PathInGroup(
        useGroup: RsUseGroup,
        override val useSpecks: List<RsUseSpeck>,
        override val basePath: String
    ) : Context {

        companion object {
            fun create(useGroup: RsUseGroup, useSpeckOnCursor: RsUseSpeck): PathInGroup? {
                val useSpeckList = mutableListOf<RsUseSpeck>()

                val basePath = useSpeckOnCursor.path?.let { getBasePathFromPath(it) } ?: return null
                useSpeckList += useSpeckOnCursor.leftSiblings.filterIsInstance<RsUseSpeck>()
                    .filter { it.path?.let { getBasePathFromPath(it) } == basePath }
                useSpeckList.add(useSpeckOnCursor)
                useSpeckList += useSpeckOnCursor.rightSiblings.filterIsInstance<RsUseSpeck>()
                    .filter { it.path?.let { getBasePathFromPath(it) } == basePath }
                if (useSpeckList.size == 1) return null

                return PathInGroup(useGroup, useSpeckList, basePath)
            }
        }

        override fun createElement(path: String, project: Project): PsiElement {
            return RsPsiFactory(project).createUseSpeck(path)
        }

        override val oldElements: List<PsiElement> =
            useSpecks.flatMap {
                val nextComma = if (it.nextSibling.elementType == RsElementTypes.COMMA) {
                    it.nextSibling
                } else {
                    null
                }
                listOf(it, nextComma)
            }.filterNotNull()

        override val firstOldElement: PsiElement = useSpecks.first()

        override val root = useGroup

        override val cursorOffset: Int = 0
    }
}

/**
 * Get base path.
 * If the path starts with :: contains it
 *
 * ex) a::b::c -> a
 * ex) ::a::b::c -> ::a
 */
fun getBasePathFromPath(path: RsPath): String {
    val basePath = path.basePath()
    val basePathColoncolon = basePath.coloncolon

    return if (basePathColoncolon != null) {
        "::${basePath.referenceName}"
    } else {
        basePath.referenceName
    }
}
