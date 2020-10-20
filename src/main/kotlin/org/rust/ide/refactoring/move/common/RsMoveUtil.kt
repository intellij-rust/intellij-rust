/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move.common

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import org.rust.ide.utils.import.insertUseItem
import org.rust.lang.core.macros.setContext
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

sealed class RsMoveUsageInfo(open val element: RsElement) : UsageInfo(element)

class RsModDeclUsageInfo(override val element: RsModDeclItem, val file: RsFile) : RsMoveUsageInfo(element)

class RsPathUsageInfo(
    override val element: RsPath,
    private val rsReference: PsiReference,
    val target: RsQualifiedNamedElement
) : RsMoveUsageInfo(element) {
    lateinit var referenceInfo: RsMoveReferenceInfo

    override fun getReference(): PsiReference = rsReference
}

class RsMoveReferenceInfo(
    var pathOld: RsPath,
    /** `null` means no accessible path found */
    val pathNewAccessible: RsPath?,
    /** Fallback path to use when `pathNew == null` (if user choose "Continue" in conflicts view) */
    val pathNewFallback: RsPath?,
    /**
     * == `pathOld.reference.resolve()`
     * mutable because it can be inside moved elements, so after move we have to change it
     */
    var target: RsQualifiedNamedElement
) {
    val pathNew: RsPath? get() = pathNewAccessible ?: pathNewFallback
    val isInsideUseDirective: Boolean get() = pathOld.ancestorStrict<RsUseItem>() != null
}

/**
 * Ideally all these functions should have package-private visibility and be at top-level.
 * But unfortunately there is no package-private visibility,
 * so we put functions inside object to decrease their priority in completion in other files.
 */
object RsMoveUtil {

    fun String.toRsPath(psiFactory: RsPsiFactory): RsPath? =
        psiFactory.tryCreatePath(this) ?: run {
            LOG.error("Can't create RsPath from '$this'")
            null
        }

    fun String.toRsPath(codeFragmentFactory: RsCodeFragmentFactory, context: RsElement): RsPath? =
        codeFragmentFactory.createPath(this, context) ?: run {
            LOG.error("Can't create RsPath from '$this' in context $context")
            null
        }

    fun String.toRsPathInEmptyTmpMod(
        codeFragmentFactory: RsCodeFragmentFactory,
        psiFactory: RsPsiFactory,
        context: RsMod
    ): RsPath? {
        val mod = psiFactory.createModItem(TMP_MOD_NAME, "")
        mod.setContext(context)
        return toRsPath(codeFragmentFactory, mod)
    }

    fun RsPath.isAbsolute(): Boolean {
        if (basePath().hasColonColon) return true
        if (startsWithSuper()) return false

        if (containingFile is DummyHolder) LOG.error("Path '$text' is inside dummy holder")
        val basePathTarget = basePath().reference?.resolve() as? RsMod ?: return false
        return basePathTarget.isCrateRoot
    }

    fun RsPath.startsWithSuper(): Boolean = basePath().referenceName == "super"

    fun RsPath.startsWithSelf(): Boolean = basePath().referenceName == "self"

    private fun RsPath.startsWithCSelf(): Boolean = basePath().referenceName == "Self"

    /**
     * Like `text`, but without spaces and comments.
     * Expected to be used for paths without type qualifiers and type arguments.
     */
    val RsPath.textNormalized: String
        get() {
            val parts = listOfNotNull(path?.textNormalized, coloncolon?.text, referenceName)
            return parts.joinToString("")
        }

    /**
     * Any outermost path has exactly one simple subpath:
     * `mod1::mod2::Struct1::method1`
     *  ^~~~~~~~~~~~~~~~~~~~~~~~~~~^ not simple
     *  ^~~~~~~~~~~~~~~~~~^ simple
     *  ^~~~~~~~~^ not simple
     *  ^~~^ not simple
     *
     * Path is simple if target of all subpaths is [RsMod]
     * (target of whole path could be [RsMod] or [RsItemElement]).
     * These paths are simple:
     * - `mod1::mod2::Struct1`
     * - `mod1::mod2::func1`
     * - `mod1::mod2::mod3` (when `parent` is not [RsPath])
     * These are not:
     * - `Struct1::func1`
     * - `Vec::<i32>::new()`
     * - `Self::Item1`
     */
    fun isSimplePath(path: RsPath): Boolean {
        // TODO: don't ignore `self::`, only `Self::` ?
        if (path.startsWithSelf() || path.startsWithCSelf()) return false
        val target = path.reference?.resolve() ?: return false
        if (target is RsMod && path.parent is RsPath) return false

        val subpaths = generateSequence(path.path) { it.path }
        return subpaths.all { it.reference?.resolve() is RsMod }
    }

    fun RsPath.resolvesToAndAccessible(target: RsQualifiedNamedElement): Boolean {
        if (containingFile is DummyHolder) LOG.error("Path '$text' is inside dummy holder")
        if (target.containingFile is DummyHolder) LOG.error("Target $target of path '$text' is inside dummy holder")
        val reference = reference ?: return false
        if (!reference.isReferenceTo(target)) return false
        return isTargetOfEachSubpathAccessible()
    }

    fun RsPath.isTargetOfEachSubpathAccessible(): Boolean {
        for (subpath in generateSequence(this) { it.path }) {
            val subpathTarget = subpath.reference?.resolve() as? RsVisible ?: continue
            if (!subpathTarget.isVisibleFrom(containingMod)) return false
        }
        return true
    }

    val RsElement.containingModOrSelf: RsMod get() = (this as? RsMod) ?: containingMod

    /**
     * @return `super` instead of `this` for [RsFile]
     *
     * ([RsMod.containingMod] returns `super` when mod is [RsModItem] and `this` when mod is [RsFile])
     */
    val RsElement.containingModStrict: RsMod
        get() = when (this) {
            is RsMod -> `super` ?: this
            else -> containingMod
        }

    /** Like [PsiElement.add], but works correctly for [RsModItem] */
    fun RsMod.addInner(element: PsiElement): PsiElement =
        addBefore(element, if (this is RsModItem) rbrace else null)

    // TODO: possible performance improvement: iterate over RsElement ancestors only once
    fun RsElement.isInsideMovedElements(elementsToMove: List<ElementToMove>): Boolean {
        if (containingFile is RsCodeFragment) LOG.error("Unexpected containingFile: $containingFile")
        return elementsToMove.any {
            when (it) {
                is ItemToMove -> PsiTreeUtil.isAncestor(it.item, this, false)
                is ModToMove -> containingModOrSelf.superMods.contains(it.mod)
            }
        }
    }

    val LOG: Logger = Logger.getInstance(RsMoveUtil::class.java)
}

inline fun <reified T : RsElement> movedElementsShallowDescendantsOfType(elementsToMove: List<ElementToMove>): List<T> =
    movedElementsShallowDescendantsOfType(elementsToMove, T::class.java)

fun <T : RsElement> movedElementsShallowDescendantsOfType(
    elementsToMove: List<ElementToMove>,
    aClass: Class<T>
): List<T> {
    return elementsToMove.flatMap {
        val element = it.element
        if (element is RsFile) return@flatMap emptyList<T>()
        PsiTreeUtil.findChildrenOfAnyType(element, false, aClass)
    }
}

inline fun <reified T : RsElement> movedElementsDeepDescendantsOfType(elementsToMove: List<ElementToMove>): Sequence<T> =
    movedElementsDeepDescendantsOfType(elementsToMove, T::class.java)

fun <T : RsElement> movedElementsDeepDescendantsOfType(
    elementsToMove: List<ElementToMove>,
    aClass: Class<T>
): Sequence<T> {
    return elementsToMove.asSequence()
        .flatMap { elementToMove ->
            when (elementToMove) {
                is ItemToMove -> PsiTreeUtil.findChildrenOfAnyType(elementToMove.item, false, aClass).asSequence()
                is ModToMove -> {
                    val mod = elementToMove.mod
                    val childModules = mod.childModules
                        .filter { it.containingFile != mod.containingFile }
                        .map { ModToMove(it) }
                    val childModulesDescendants = movedElementsDeepDescendantsOfType(childModules, aClass)
                    val selfDescendants = PsiTreeUtil.findChildrenOfAnyType(mod, false, aClass).asSequence()
                    selfDescendants + childModulesDescendants
                }
            }
        }
}

/**
 * Updates `pub(in path)` visibility modifier.
 * We replace `path` with common parent module of `newParent` and target of old `path`.
 */
fun RsVisRestriction.updateScopeIfNecessary(psiFactory: RsPsiFactory, newParent: RsMod) {
    if (crateRoot == newParent.crateRoot) {
        if (path.kind == PathKind.SELF) return  // `pub(self)`
        // TODO: pass `oldScope` as parameter?
        val oldScope = path.reference?.resolve() as? RsMod ?: return
        val newScope = commonParentMod(oldScope, newParent) ?: return
        val newScopePath = newScope.crateRelativePath ?: return
        val newVisRestriction = psiFactory.createVisRestriction("crate$newScopePath")
        replace(newVisRestriction)
    } else {
        // RsVisibility has text `pub(in path)`
        // after removing RsVisRestriction it will be changed to `pub`
        delete()
    }
}

fun addImport(psiFactory: RsPsiFactory, context: RsElement, usePath: String) {
    if (!usePath.contains("::")) return
    val blockScope = context.ancestors.find { it is RsBlock && it.childOfType<RsUseItem>() != null } as RsBlock?
    check(context !is RsMod)
    val scope = blockScope ?: context.containingMod
    scope.insertUseItem(psiFactory, usePath)
}
