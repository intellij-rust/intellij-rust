/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.openapi.paths.GlobalPathReferenceProvider
import com.intellij.openapi.paths.WebReference
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import com.intellij.util.SmartList
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.RsFileType
import org.rust.lang.core.RsPsiPattern.includeMacroLiteral
import org.rust.lang.core.RsPsiPattern.literal
import org.rust.lang.core.RsPsiPattern.pathAttrLiteral
import org.rust.lang.core.or
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psiElement
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.types.*
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyAnon
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.with
import org.rust.lang.utils.parseRustStringCharacters

class RsLitExprReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(includeMacroLiteral or pathAttrLiteral or pathValueLiteral, RsFileReferenceProvider())
        // LOWER_PRIORITY is used to have ability to override reference if needed
        registrar.registerReferenceProvider(literal, RsLiteralGeneralUrlProvider(), PsiReferenceRegistrar.LOWER_PRIORITY)
        registrar.registerReferenceProvider(literal, RsLiteralGitHubIssueProvider())
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
            is RsIncludeMacroArgument -> parentDirectoryContext
            else -> {
                val rsElement = element as? RsElement ?: return emptyList()
                val workspaceRoot = rsElement.containingCargoPackage?.workspace?.workspaceRoot ?: return emptyList()
                return toFileSystemItems(workspaceRoot)
            }
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
    val ownerType = (owner?.impl?.typeReference?.normType as? TyAdt)?.item
    val (implLookup, knownItems) = function.implLookupAndKnownItems
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
    val type = parameter.typeReference?.rawType as? TyTypeParameter ?: return false
    return lookup.getEnvBoundTransitivelyFor(type).any { isAsRefPath(knownItems, it) }
}

// fn foo(p: impl AsRef<Path>)
private fun isImplAsRefPath(knownItems: KnownItems, parameter: RsValueParameter): Boolean {
    val type = parameter.typeReference?.rawType as? TyAnon ?: return false
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

private abstract class RsLiteralWebReferenceProviderBase : PsiReferenceProvider() {

    // Web references do not point to any real PsiElement
    override fun acceptsTarget(target: PsiElement): Boolean = false

    final override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val expr = element as? RsLitExpr ?: return PsiReference.EMPTY_ARRAY

        return when (val literal = expr.kind) {
            is RsLiteralKind.String -> getReferencesByStringLiteral(expr, literal)
            else -> PsiReference.EMPTY_ARRAY
        }
    }

    protected abstract fun getReferencesByStringLiteral(expr: RsLitExpr, literal: RsLiteralKind.String): Array<PsiReference>
}

private class RsLiteralGeneralUrlProvider : RsLiteralWebReferenceProviderBase() {

    override fun getReferencesByStringLiteral(expr: RsLitExpr, literal: RsLiteralKind.String): Array<PsiReference> {
        // Url should contain `:` symbol.
        // In combination with the fact that `textContains` doesn't allocate memory,
        // it makes quite cheap check
        if (!expr.textContains(':')) return PsiReference.EMPTY_ARRAY

        val valueRange = literal.offsets.value ?: return PsiReference.EMPTY_ARRAY
        val rawValue = literal.rawValue ?: return PsiReference.EMPTY_ARRAY
        val (content, indexFn) = if (literal.node.elementType in RS_RAW_LITERALS) {
            rawValue to { i: Int -> i }
        } else {
            val (content, indices) = parseRustStringCharacters(rawValue)
            content to { i: Int -> indices[i] }
        }

        val references = SmartList<PsiReference>()
        var index = 0
        for (word in content.split(SPACE_SPLITTER)) {
            if (word.isNotEmpty() && GlobalPathReferenceProvider.isWebReferenceUrl(word)) {
                val startOffset = valueRange.startOffset + indexFn(index)
                val endOffset = valueRange.startOffset + indexFn(index + word.length)
                references += WebReference(expr, TextRange(startOffset, endOffset), word)
            }
            index += word.length + 1
        }

        return references.toArray(PsiReference.EMPTY_ARRAY)
    }

    companion object {
        private val SPACE_SPLITTER: Regex = Regex("\\s")
    }
}

/**
 * Current support:
 *
 * [rust-lang](https://github.com/rust-lang/rust)
 * - [RsInnerAttr] / [RsOuterAttr] - `unstable` - `issue`
 * - [RsInnerAttr]? / [RsOuterAttr] - `rustc_const_unstable` - `issue`
 */
private class RsLiteralGitHubIssueProvider : RsLiteralWebReferenceProviderBase() {

    override fun getReferencesByStringLiteral(expr: RsLitExpr, literal: RsLiteralKind.String): Array<PsiReference> {
        if (expr.containingCrate.origin != PackageOrigin.STDLIB) return PsiReference.EMPTY_ARRAY

        val exprMetaItem = expr.parent as? RsMetaItem ?: return PsiReference.EMPTY_ARRAY

        if (exprMetaItem.name != "issue") return PsiReference.EMPTY_ARRAY

        val issueNumber = literal.value?.toLongOrNull() ?: return PsiReference.EMPTY_ARRAY
        val metaItem = (exprMetaItem.parent as? RsMetaItemArgs)?.parent as? RsMetaItem ?: return PsiReference.EMPTY_ARRAY

        return if (metaItem.isRootMetaItem() && metaItem.name in RUST_ISSUE_ATTR_NAMES) {
            arrayOf(WebReference(expr, literal.offsets.value, "https://github.com/rust-lang/rust/issues/${issueNumber}"))
        } else {
            PsiReference.EMPTY_ARRAY
        }
    }

    companion object {
        private val RUST_ISSUE_ATTR_NAMES = setOf("unstable", "rustc_const_unstable")
    }
}
