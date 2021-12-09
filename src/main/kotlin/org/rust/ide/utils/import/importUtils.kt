/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.import

import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.refactoring.RsImportOptimizer.Companion.sortUseSpecks
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.setContext
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.index.RsIncludeMacroIndex
import org.rust.openapiext.Testmark
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.stdext.isSortedWith

/**
 * Inserts a use declaration to the mod where [context] located for importing the selected candidate ([this]).
 * This action requires write access.
 */
fun ImportCandidateBase.import(context: RsElement) {
    checkWriteAccessAllowed()
    val psiFactory = RsPsiFactory(context.project)

    // depth of `mod` relative to module with `extern crate` item
    // we use this info to create correct relative use item path if needed
    val relativeDepth = info.insertExternCrateIfNeeded(context)
    val prefix = when (relativeDepth) {
        null -> ""
        0 -> "self::"
        else -> "super::".repeat(relativeDepth)
    }

    val containingFile = context.containingFile
    val insertionScope = when {
        context.isDoctestInjection -> {
            // In doctest injections all our code is located inside one invisible (main) function.
            // If we try to change PSI outside of that function, we'll take a crash.
            // So here we limit the module search with the last function (and never inert to an RsFile)
            Testmarks.doctestInjectionImport.hit()
            val scope = context.ancestors.find { it is RsMod && it !is RsFile }
                ?: context.ancestors.findLast { it is RsFunction }
            ((scope as? RsFunction)?.block ?: scope) as RsItemsOwner
        }
        containingFile is RsCodeFragment -> containingFile.importTarget
        containingFile is RsFile && RsIncludeMacroIndex.getIncludedFrom(containingFile) != null -> containingFile
        else -> null
    } ?: context.containingMod
    insertionScope.insertUseItem(psiFactory, "$prefix${info.usePath}")
}

/**
 * Inserts an `extern crate` item if the crate of importing element differs from the crate of `context`.
 * Returns the relative depth of context `mod` relative to module with `extern crate` item.
 */
fun ImportInfo.insertExternCrateIfNeeded(context: RsElement): Int? {
    if (this is ImportInfo.ExternCrateImportInfo) {
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
                if (needInsertExternCrateItem && !context.isAtLeastEdition2018) {
                    crateRoot?.insertExternCrateItem(RsPsiFactory(context.project), externCrateName)
                } else {
                    if (depth != null) {
                        Testmarks.externCrateItemInNotCrateRoot.hit()
                        return depth
                    }
                }
            }
        }
    }
    return null
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
    useItem.setContext(containingMod)  // needed for correct sorting of added import
    insertUseItem(psiFactory, useItem)
}

fun RsItemsOwner.insertUseItem(psiFactory: RsPsiFactory, useItem: RsUseItem) {
    if (tryGroupWithOtherUseItems(psiFactory, useItem)) return
    if (tryInsertUseItemAtCorrectLocation(this, useItem)) return

    // else handle case when mod is empty or has no `use`s / `extern crate`s
    // `if` is needed to support adding import to empty inline mod (see `RsCodeFragment#importTarget`)
    addBefore(useItem, if (this is RsModItem && itemsAndMacros.none()) rbrace else firstItem)
    addAfter(psiFactory.createNewline(), firstItem)
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

    val isUseSpeckSorted = useSpeck?.useGroup?.useSpeckList?.isSortedWith(COMPARATOR_FOR_SPECKS_IN_USE_GROUP) ?: true
    if (isUseSpeckSorted) {
        newUseSpeck.useGroup?.sortUseSpecks()
    }

    useSpeck?.replace(newUseSpeck)
    return true
}

private fun tryInsertUseItemAtCorrectLocation(mod: RsItemsOwner, useItem: RsUseItem): Boolean {
    val newline = RsPsiFactory(mod.project).createNewline()
    val uses = mod.childrenOfType<RsUseItem>().map(::UseItemWrapper)
    if (uses.isEmpty()) {
        val anchor = mod.childrenOfType<RsExternCrateItem>().lastOrNull() ?: return false
        mod.addBefore(newline, mod.addAfter(useItem, anchor))
        return true
    }

    val useWrapper = UseItemWrapper(useItem)
    val (less, greater) = uses.partition { it < useWrapper }
    val anchorBefore = less.lastOrNull()
    val anchorAfter = greater.firstOrNull()
    when {
        anchorBefore != null -> {
            val addedItem = mod.addAfter(useItem, anchorBefore.useItem)
            mod.addBefore(newline, addedItem)
        }
        anchorAfter != null -> {
            val addedItem = mod.addBefore(useItem, anchorAfter.useItem)
            mod.addAfter(newline, addedItem)
        }
        else -> error("unreachable")
    }
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
val <T : RsElement> List<T>.lastElement: T? get() = maxByOrNull { it.textOffset }

val RsElement.stdlibAttributes: RsFile.Attributes
    get() = (crateRoot?.containingFile as? RsFile)?.stdlibAttributes ?: RsFile.Attributes.NONE

val Crate.isStd: Boolean
    get() = origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.STD

val Crate.isCore: Boolean
    get() = origin == PackageOrigin.STDLIB && normName == AutoInjectedCrates.CORE

object Testmarks {
    val autoInjectedStdCrate = Testmark("autoInjectedStdCrate")
    val autoInjectedCoreCrate = Testmark("autoInjectedCoreCrate")
    val externCrateItemInNotCrateRoot = Testmark("externCrateItemInNotCrateRoot")
    val doctestInjectionImport = Testmark("doctestInjectionImport")
    val ignorePrivateImportInParentMod = Testmark("ignorePrivateImportInParentMod")
}

/**
 * Create a fake mod that can be used for completion in code fragments to avoid importing
 * types into the real mod of the element.
 *
 * Use this mod as the importContext of code fragments.
 */
fun RsElement.createVirtualImportContext(): RsMod {
    val factory = RsPsiFactory(project)
    val sourceContext = containingFile as RsFile

    val qualifiedPath = containingMod.qualifiedNameInCrate(this)
    val defaultUseItem = if (qualifiedPath != null) {
        "use $qualifiedPath::*;"
    } else {
        ""
    }
    val module = factory.createModItem(TMP_MOD_NAME, defaultUseItem)

    module.setContext(sourceContext)
    return module
}
