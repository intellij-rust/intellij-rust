/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineTypeAlias

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.rust.RsBundle
import org.rust.ide.fixes.deleteUseSpeck
import org.rust.ide.intentions.SubstituteTypeAliasIntention
import org.rust.ide.presentation.getStubOnlyText
import org.rust.ide.refactoring.RsInlineUsageViewDescriptor
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.macros.isExpandedFromMacro
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.searchReferences
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.openapiext.testAssert

/** See also [SubstituteTypeAliasIntention] */
class RsInlineTypeAliasProcessor(
    project: Project,
    private val typeAlias: RsTypeAlias,
    private val reference: RsReference?,
    private val inlineThisOnly: Boolean,
) : BaseRefactoringProcessor(project) {

    override fun findUsages(): Array<UsageInfo> {
        val usages = if (inlineThisOnly && reference != null) {
            listOf(reference)
        } else {
            typeAlias.searchReferences(typeAlias.useScope)
        }
        return usages
            .map { UsageInfo(it) }
            .toTypedArray()
    }

    private class PathUsage(val path: RsPath, val substitution: Substitution)

    override fun performRefactoring(usages: Array<UsageInfo>) {
        val (pathUsages, useSpecks, hasOtherUsages) = partitionUsages(usages)

        val typeReference = typeAlias.typeReference ?: return
        val inlined = pathUsages.mapNotNull {
            fillPathWithActualType(it.path, typeReference, it.substitution)
        }
        for (useSpeck in useSpecks) {
            deleteUseSpeck(useSpeck)
        }
        addNeededImports(inlined)

        if (!inlineThisOnly && !hasOtherUsages && inlined.size == pathUsages.size) {
            (typeAlias.prevSibling as? PsiWhiteSpace)?.delete()
            typeAlias.delete()
        }
    }

    private fun partitionUsages(usages: Array<UsageInfo>): Triple<List<PathUsage>, List<RsUseSpeck>, Boolean> {
        val paths = mutableListOf<PathUsage>()
        val useSpecks = mutableListOf<RsUseSpeck>()
        for (usage in usages) {
            val path = usage.element as? RsPath ?: continue
            if (path.isExpandedFromMacro) continue
            val useSpeck = path.ancestorOrSelf<RsUseSpeck>()
            if (useSpeck != null) {
                useSpecks += useSpeck
            } else {
                val resolved = path.reference?.advancedResolve() ?: continue
                testAssert { resolved.element == typeAlias }
                val substitution = tryGetTypeAliasSubstitutionUsingParent(path, typeAlias) ?: resolved.subst
                paths += PathUsage(path, substitution)
            }
        }
        val hasOtherUsages = paths.size + useSpecks.size != usages.size
        return Triple(paths, useSpecks, hasOtherUsages)
    }

    private fun addNeededImports(inlined: List<RsElement>) {
        val typeReference = typeAlias.typeReference ?: return
        val handledMods = hashSetOf(typeAlias.containingMod)
        for (context in inlined) {
            val mod = context.containingMod
            if (!handledMods.add(mod)) continue
            RsImportHelper.importTypeReferencesFromElement(context, typeReference)
        }
    }

    override fun getCommandName(): String = RsBundle.message("command.name.inline.type.alias", typeAlias.name?:"")

    override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor =
        RsInlineUsageViewDescriptor(typeAlias, RsBundle.message("list.item.type.alias.to.inline"))
}

/**
 * type Foo<T> = Vec<T>;
 * fn main() {
 *     let v = Foo::new();
 *             ~~~ has substitution `T => T`
 *             ~~~~~~~~ has substitution `Self => Vec<i32>`
 *     v.push(1);
 * }
 */
fun tryGetTypeAliasSubstitutionUsingParent(path: RsPath, typeAlias: RsTypeAlias): Substitution? {
    val parentPath = path.parent as? RsPath ?: return null
    if (parentPath.parent !is RsPathExpr) return null
    val resolvedMethod = parentPath.reference?.advancedResolve() ?: return null
    val selfTy = resolvedMethod.subst[TyTypeParameter.self()] ?: return null
    val inference = path.implLookup.ctx
    val subst = inference.instantiateBounds(typeAlias, selfTy)
    val typeReference = typeAlias.typeReference ?: return null
    val type = typeReference.normType.substitute(subst)
    inference.combineTypes(type, selfTy)
    return subst.mapTypeValues { (_, v) -> inference.resolveTypeVarsIfPossible(v) }
}

/**
 * type Foo<T> = Vec<T>;
 *               ~~~~~~ type
 * fn main() {
 *     let _: Foo<i32>;
 *            ~~~~~~~~ path
 * }                   subst = "T => i32"
 */
fun fillPathWithActualType(path: RsPath, typeReference: RsTypeReference, substitution: Substitution): RsElement? {
    // TODO: Render qualified paths if needed (see `test qualify path`)
    val typeText = typeReference.getStubOnlyText(substitution, shortPaths = false)
    return fillPathWithActualType(path, typeText)
}

private fun fillPathWithActualType(path: RsPath, typeText: String): RsElement? {
    val factory = RsPsiFactory(path.project)
    val typeReference = factory.tryCreateType(typeText) ?: return null
    val typeAsPath = (typeReference as? RsPathType)?.path

    val parent = path.parent
    return when {
        parent is RsTypeReference -> {
            parent.replace(typeReference)
        }

        typeAsPath != null -> {
            if (path.ancestorStrict<RsTypeReference>() == null) {
                // Consider `type Foo = HashSet<i32>;`
                // Replace `Foo::new()` to `HashSet::<i32>::new()`
                typeAsPath.typeArgumentList?.addAfter(factory.createColonColon(), null)
            }

            path.replace(typeAsPath)
        }

        parent is RsPath -> {
            // Consider `type Foo = [i32];`
            // Replace `Foo::is_empty(x)` to `<[i32]>::is_empty(x)`
            val parentName = parent.referenceName ?: return null
            val parentTypeArguments = parent.typeArgumentList?.text.orEmpty()
            val parentNewText = "<$typeText>::$parentName$parentTypeArguments"
            val parentNew = factory.tryCreatePath(parentNewText) ?: return null
            parent.replace(parentNew)
        }

        // should be no other cases
        else -> return null
    } as RsElement
}
