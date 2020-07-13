/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move.common

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.rust.ide.refactoring.move.common.RsMoveUtil.containingModOrSelf
import org.rust.ide.refactoring.move.common.RsMoveUtil.resolvesToAndAccessible
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.RsCodeFragmentFactory
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.ext.*

/**
 * Consider we move item with name `foo` from `mod1` to `mod2`
 * before move we should check visibility conflicts (whether we can update all references to moved item after move)
 * so for each reference (RsPath) we should find replacement (new RsPath)
 * this helper checks possible new paths in the following order:
 * 1. find any public item in `mod2`, find path to it using RsImportHelper, and replace path last segment
 * 2. find path to `mod2` using RsImportHelper, and add last segment (with moved item name)
 *
 * It will not work if there is glob reexport for items in `mod2` and `mod2` has no public items
 * But it is somewhat strange case (why someone would reexport everything from `mod2` if there are no pub items?)
 * This can be fixed by adding new item to `mod2` in such cases
 * (Though it is unclear how to use writeAction only for small amount of time and not for all preprocess usages stage)
 */
class RsMovePathHelper(private val project: Project, private val mod: RsMod) {

    private val codeFragmentFactory: RsCodeFragmentFactory = RsCodeFragmentFactory(project)
    private val existingPublicItem: RsQualifiedNamedElement? = findExistingPublicItem()

    private fun findExistingPublicItem(): RsQualifiedNamedElement? =
        mod.childrenOfType<RsQualifiedNamedElement>()
            .filter { it is RsVisibilityOwner && it.visibility == RsVisibility.Public && it.name != null }
            // reference search works faster for not common names
            .sortedByDescending { it.name?.length }
            .firstOrNull { item ->
                // have to filter existing items which are publicly reexported (not glob)
                // because for our new item we can't use that (not glob) reexport
                val itemUsages = ReferencesSearch.search(item, GlobalSearchScope.projectScope(project))
                itemUsages.none { it.element.parentOfType<RsUseItem>()?.vis != null }
            }

    fun findPathAfterMove(context: RsElement, element: RsQualifiedNamedElement): RsPath? {
        val elementName = (element as? RsFile)?.modName ?: element.name ?: return null
        if (context.containingModOrSelf == mod) return codeFragmentFactory.createPath(elementName, context)

        return findPathAfterMoveUsingOtherItemInMod(context, elementName)
            ?: findPathAfterMoveUsingMod(context, elementName)
    }

    private fun findPathAfterMoveUsingOtherItemInMod(context: RsElement, elementName: String): RsPath? {
        val secondaryElement = existingPublicItem ?: return null
        val secondaryElementName = secondaryElement.name ?: return null
        val secondaryPathText = findPath(context, secondaryElement)
            ?: return null
        val secondaryPath = codeFragmentFactory.createPath(secondaryPathText, context) ?: return null
        if (secondaryPath.reference!!.resolve() != secondaryElement) return null

        if (!secondaryPathText.endsWith("::$secondaryElementName")) return null
        val pathText = secondaryPathText.removeSuffix(secondaryElementName) + elementName
        return codeFragmentFactory.createPath(pathText, context)
    }

    private fun findPathAfterMoveUsingMod(context: RsElement, elementName: String): RsPath? {
        val modPath = findPath(context, mod) ?: return null
        val elementPath = "$modPath::$elementName"
        return codeFragmentFactory.createPath(elementPath, context)
    }

    // basically it returns `element.crateRelativePath`
    // but if `element.crateRelativePath`, is inaccessible in context (e.g. because of reexports),
    // then tries to find other path using RsImportHelper
    private fun findPath(context: RsElement, element: RsQualifiedNamedElement): String? {
        val pathSimple = findPathSimple(context, element)
        if (pathSimple != null) return pathSimple

        val path = RsImportHelper.findPath(context, element) ?: return null
        return convertPathToRelativeIfPossible(context.containingModOrSelf, path)
    }

    // returns `element.crateRelativePath` if it is accessible from `context`
    private fun findPathSimple(context: RsElement, element: RsQualifiedNamedElement): String? {
        val contextMod = context.containingModOrSelf
        val pathText = element.qualifiedNameRelativeTo(contextMod) ?: return null
        val path = codeFragmentFactory.createPath(pathText, context) ?: return null
        return if (path.resolvesToAndAccessible(element)) path.text else null
    }
}
