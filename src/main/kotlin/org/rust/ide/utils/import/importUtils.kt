/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.import

import com.intellij.openapiext.Testmark
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.ide.injected.isDoctestInjection
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.checkWriteAccessAllowed

/**
 * Inserts a use declaration to the mod where [context] located for importing the selected candidate ([this]).
 * This action requires write access.
 */
fun ImportCandidate.import(context: RsElement) {
    checkWriteAccessAllowed()
    val psiFactory = RsPsiFactory(context.project)
    // depth of `mod` relative to module with `extern crate` item
    // we uses this info to create correct relative use item path if needed
    var relativeDepth: Int? = null

    val isEdition2018 = context.isEdition2018
    val info = info
    // if crate of importing element differs from current crate
    // we need to add new extern crate item
    if (info is ImportInfo.ExternCrateImportInfo) {
        val crate = info.crate
        val crateRoot = context.crateRoot
        val attributes = crateRoot?.stdlibAttributes ?: RsFile.Attributes.NONE
        when {
            // but if crate of imported element is `std` and there aren't `#![no_std]` and `#![no_core]`
            // we don't add corresponding extern crate item manually
            // because it will be done by compiler implicitly
            attributes == RsFile.Attributes.NONE && crate.isStd -> Testmarks.autoInjectedStdCrate.hit()
            // if crate of imported element is `core` and there is `#![no_std]`
            // we don't add corresponding extern crate item manually for the same reason
            attributes == RsFile.Attributes.NO_STD && crate.isCore -> Testmarks.autoInjectedCoreCrate.hit()
            else -> {
                if (info.needInsertExternCrateItem && !isEdition2018) {
                    crateRoot?.insertExternCrateItem(psiFactory, info.externCrateName)
                } else {
                    if (info.depth != null) {
                        Testmarks.externCrateItemInNotCrateRoot.hit()
                        relativeDepth = info.depth
                    }
                }
            }
        }
    }
    val prefix = when (relativeDepth) {
        null -> ""
        0 -> "self::"
        else -> "super::".repeat(relativeDepth)
    }

    val insertionScope = if (context.isDoctestInjection) {
        // In doctest injections all our code is located inside one invisible (main) function.
        // If we try to change PSI outside of that function, we'll take a crash.
        // So here we limit the module search with the last function (and never inert to an RsFile)
        Testmarks.doctestInjectionImport.hit()
        val scope = context.ancestors.find { it is RsMod && it !is RsFile }
            ?: context.ancestors.findLast { it is RsFunction }
        ((scope as? RsFunction)?.block ?: scope) as RsItemsOwner
    } else {
        context.containingMod
    }
    insertionScope.insertUseItem(psiFactory, "$prefix${info.usePath}")
}

private fun RsMod.insertExternCrateItem(psiFactory: RsPsiFactory, crateName: String) {
    val externCrateItem = psiFactory.createExternCrateItem(crateName)
    val lastExternCrateItem = childrenOfType<RsExternCrateItem>().lastElement
    if (lastExternCrateItem != null) {
        addAfter(externCrateItem, lastExternCrateItem)
    } else {
        addBefore(externCrateItem, firstItem)
        addAfter(psiFactory.createNewline(), firstItem)
    }
}

fun RsItemsOwner.insertUseItem(psiFactory: RsPsiFactory, usePath: String) {
    val useItem = psiFactory.createUseItem(usePath)
    if (tryGroupWithOtherUseItems(psiFactory, useItem)) return
    val anchor = childrenOfType<RsUseItem>().lastElement ?: childrenOfType<RsExternCrateItem>().lastElement
    if (anchor != null) {
        val insertedUseItem = addAfter(useItem, anchor)
        if (anchor is RsExternCrateItem || isDoctestInjection) {
            // Formatting is disabled in injections, so we have to add new line manually
            Testmarks.insertNewLineBeforeUseItem.hit()
            addBefore(psiFactory.createNewline(), insertedUseItem)
        }
    } else {
        addBefore(useItem, firstItem)
        addAfter(psiFactory.createNewline(), firstItem)
    }
}

private fun RsItemsOwner.tryGroupWithOtherUseItems(psiFactory: RsPsiFactory, newUseItem: RsUseItem): Boolean {
    val newParentPath = newUseItem.parentPath ?: return false
    val newImportingName = newUseItem.importingNames?.singleOrNull() ?: return false
    return childrenOfType<RsUseItem>().any { it.tryGroupWith(psiFactory, newParentPath, newImportingName) }
}

private fun RsUseItem.tryGroupWith(
    psiFactory: RsPsiFactory,
    newParentPath: List<String>,
    newImportingName: String
): Boolean {
    if (vis != null || outerAttrList.isNotEmpty() || useSpeck?.isStarImport == true) return false
    val parentPath = parentPath ?: return false
    if (parentPath != newParentPath) return false
    val importingNames = importingNames ?: return false
    if (importingNames.contains(newImportingName)) return true
    val newUsePath = parentPath.joinToString("::", postfix = "::") +
        (importingNames + newImportingName).joinToString(", ", "{", "}")
    val newUseSpeck = psiFactory.createUseSpeck(newUsePath)
    useSpeck?.replace(newUseSpeck)
    return true
}

private val RsUseItem.parentPath: List<String>?
    get() {
        val path = pathAsList ?: return null
        return if (useSpeck?.useGroup != null) path else path.dropLast(1)
    }

private val RsUseItem.importingNames: Set<String>?
    get() {
        if (useSpeck?.isStarImport == true) return null
        val path = pathAsList ?: return null
        val groupedNames = useSpeck?.useGroup?.useSpeckList?.asSequence()?.map { it.text }?.toSet()
        val lastName = path.lastOrNull()
        val alias = useSpeck?.alias?.identifier?.text
        return when {
            groupedNames != null -> groupedNames
            lastName != null && alias != null -> setOf("$lastName as $alias")
            lastName != null -> setOf(lastName)
            else -> null
        }
    }

private val RsUseItem.pathAsList: List<String>?
    get() = useSpeck?.path?.text?.split("::")

private val RsItemsOwner.firstItem: RsElement get() = itemsAndMacros.first { it !is RsAttr && it !is RsVis }
val <T : RsElement> List<T>.lastElement: T? get() = maxBy { it.textOffset }

val RsElement.stdlibAttributes: RsFile.Attributes
    get() = (crateRoot?.containingFile as? RsFile)?.attributes ?: RsFile.Attributes.NONE

val Crate.isStd: Boolean
    get() = origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.STD

val Crate.isCore: Boolean
    get() = origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.CORE

object Testmarks {
    val autoInjectedStdCrate = Testmark("autoInjectedStdCrate")
    val autoInjectedCoreCrate = Testmark("autoInjectedCoreCrate")
    val externCrateItemInNotCrateRoot = Testmark("externCrateItemInNotCrateRoot")
    val doctestInjectionImport = Testmark("doctestInjectionImport")
    val insertNewLineBeforeUseItem = Testmark("insertNewLineBeforeUseItem")
}
