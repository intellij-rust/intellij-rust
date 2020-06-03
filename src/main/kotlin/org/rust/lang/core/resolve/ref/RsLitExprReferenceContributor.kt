/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.Condition
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.rust.lang.RsFileType
import org.rust.lang.core.RsPsiPattern.includeMacroLiteral
import org.rust.lang.core.RsPsiPattern.pathAttrLiteral
import org.rust.lang.core.or
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psiElement
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.implLookupAndKnownItems
import org.rust.lang.core.types.positionalTypeArguments
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyAnon
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type
import org.rust.lang.core.with

class RsLitExprReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(includeMacroLiteral or pathAttrLiteral or pathValueLiteral, RsFileReferenceProvider())
    }
}

private class RsFileReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<FileReference> {
        val stringLiteral = (element as? RsLitExpr)?.kind as? RsLiteralKind.String ?: return emptyArray()
        if (stringLiteral.isByte) return emptyArray()
        val startOffset = stringLiteral.offsets.value?.startOffset ?: return emptyArray()
        val fs = element.containingFile.originalFile.virtualFile.fileSystem
        val literalValue = stringLiteral.value ?: ""
        return RsLiteralFileReferenceSet(literalValue, element, startOffset, fs.isCaseSensitive).allReferences
    }
}

private class RsLiteralFileReferenceSet(
    str: String,
    element: RsLitExpr,
    startOffset: Int,
    isCaseSensitive: Boolean
) : FileReferenceSet(str, element, startOffset, null, isCaseSensitive) {

    override fun getDefaultContexts(): Collection<PsiFileSystemItem> {
        return when (val parent = element.parent) {
            is RsMetaItem -> {
                val item = parent.ancestorStrict<RsModDeclItem>() ?: parent.ancestorStrict<RsMod>()
                listOfNotNull(item?.containingMod?.getOwnedDirectory())
            }
            else -> parentDirectoryContext
        }
    }

    override fun getReferenceCompletionFilter(): Condition<PsiFileSystemItem> {
        return when (element.parent) {
            is RsMetaItem -> Condition { item ->
                if (item.isDirectory) return@Condition true
                item.virtualFile.fileType == RsFileType
            }
            else -> super.getReferenceCompletionFilter()
        }
    }
}

private val pathValueLiteral: PsiElementPattern.Capture<RsLitExpr> = psiElement<RsLitExpr>()
    .with("onPathLiteral") { expr -> expr.kind is RsLiteralKind.String && isPathLitExpr(expr) }

private fun isPathLitExpr(expr: RsLitExpr): Boolean {
    val (function, arguments) = getFunctionAndArguments(expr) ?: return false
    val owner = function.owner as? RsAbstractableOwner.Impl
    val ownerType = (owner?.impl?.typeReference?.type as? TyAdt)?.item
    val (implLookup, knownItems) = expr.implLookupAndKnownItems
    val isCallExpr = arguments.parent is RsCallExpr

    return when {
        // Path::new(<literal>)
        function.name == "new" &&
            owner?.isInherent == true &&
            ownerType == knownItems.Path -> true
        // PathBuf::from(<literal>)
        function.name == "from" &&
            ownerType == knownItems.PathBuf &&
            owner?.impl?.traitRef?.path?.reference?.resolve() == knownItems.From -> true
        // fn foo<P: AsRef<Path>>(p: P) -> foo(<literal>)
        // or fn foo(p: impl AsRef<Path>) -> foo(<literal>)
        else -> {
            var argumentIndex = arguments.exprList.indexOf(expr)

            // UFCS of a method
            if (function.hasSelfParameters && isCallExpr) {
                argumentIndex -= 1
            }

            if (argumentIndex < 0) return false

            val parameter = function.valueParameters.getOrNull(argumentIndex) ?: return false
            isAsRefPathGeneric(implLookup, knownItems, parameter) || isImplAsRefPath(knownItems, parameter)
        }
    }
}

// fn foo<P: AsRef<Path>>(p: P)
private fun isAsRefPathGeneric(lookup: ImplLookup, knownItems: KnownItems, parameter: RsValueParameter): Boolean {
    val type = parameter.typeReference?.type as? TyTypeParameter ?: return false
    return lookup.getEnvBoundTransitivelyFor(type).any { isAsRefPath(knownItems, it) }
}

// fn foo(p: impl AsRef<Path>)
private fun isImplAsRefPath(knownItems: KnownItems, parameter: RsValueParameter): Boolean {
    val type = parameter.typeReference?.type as? TyAnon ?: return false
    return type.traits.any { isAsRefPath(knownItems, it) }
}

private fun isAsRefPath(knownItems: KnownItems, trait: BoundElement<RsTraitItem>): Boolean =
    trait.element == knownItems.AsRef && (trait.positionalTypeArguments.getOrNull(0) as? TyAdt)?.item == knownItems.Path

private fun getFunctionAndArguments(expr: RsLitExpr): Pair<RsFunction, RsValueArgumentList>? {
    return when (val grandParent = expr.context?.context) {
        is RsCallExpr -> {
            val path = (grandParent.expr as? RsPathExpr)?.path ?: return null
            val function = path.reference?.resolve() as? RsFunction ?: return null
            function to grandParent.valueArgumentList
        }
        is RsMethodCall -> {
            val function = grandParent.reference.resolve() as? RsFunction ?: return null
            function to grandParent.valueArgumentList
        }
        else -> null
    }
}
