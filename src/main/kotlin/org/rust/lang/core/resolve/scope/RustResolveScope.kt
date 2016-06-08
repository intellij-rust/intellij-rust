package org.rust.lang.core.resolve.scope

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.cargo.util.cargoProject
import org.rust.cargo.util.getPsiFor
import org.rust.cargo.util.preludeModule
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.mixin.isStarImport
import org.rust.lang.core.psi.impl.rustMod
import org.rust.lang.core.psi.util.module

interface RustResolveScope : RustCompositeElement {
    interface Entry {
        val name: String
        val element: RustNamedElement?
    }
}

fun RustResolveScope.declarations(place: RustCompositeElement? = null): Sequence<RustResolveScope.Entry> =
    declarations(Context(place))

private fun RustResolveScope.declarations(context: Context): Sequence<RustResolveScope.Entry> = Sequence {
    val visitor = RustScopeVisitor(context)
    accept(visitor)
    visitor.result.iterator()
}

private data class Context(
    val place: RustCompositeElement?,
    val inPrelude: Boolean = false,
    val visitedStarImports: Set<RustUseItemElement> = emptySet()
)

private class ScopeEntryImpl private constructor(
    override val name: String,
    private val thunk: Lazy<RustNamedElement?>
) : RustResolveScope.Entry {
    override val element: RustNamedElement? by thunk

    companion object {
        fun of(element: RustNamedElement): RustResolveScope.Entry? = element.name?.let {
            ScopeEntryImpl(it, lazyOf(element))
        }

        fun lazy(name: String?, thunk: () -> RustNamedElement?): RustResolveScope.Entry? = name?.let {
            ScopeEntryImpl(name, lazy(thunk))
        }
    }

    override fun toString(): String {
        return "ScopeEntryImpl(name='$name', thunk=$thunk)"
    }
}

private class RustScopeVisitor(
    val context: Context
) : RustElementVisitor() {
    lateinit var result: Sequence<RustResolveScope.Entry>

    override fun visitElement(element: PsiElement): Unit =
        throw IllegalStateException("Unhandled RustResolveScope: $element")

    override fun visitModItem(o: RustModItemElement) {
        visitMod(o)
    }

    override fun visitFile(file: PsiFile) {
        visitMod(file as RustFile)
    }

    override fun visitForExpr(o: RustForExprElement) {
        result = o.scopedForDecl.boundElements.scopeEntries
    }

    override fun visitScopedLetDecl(o: RustScopedLetDeclElement) {
        result = if (context.place == null || !PsiTreeUtil.isAncestor(o, context.place, true)) {
            o.boundElements.scopeEntries
        } else emptySequence()
    }

    override fun visitBlock(o: RustBlockElement) {
        // If place is specified in context, we want to filter out
        // all non strictly preceding let declarations.
        //
        // ```
        // let x = 92; // visible
        // let x = x;  // not visible
        //         ^ context.place
        // let x = 62; // not visible
        // ```
        val allLetDecls = o.stmtList.asReversed().asSequence().filterIsInstance<RustLetDeclElement>()
        val visibleLetDecls = if (context.place == null)
            allLetDecls
        else
            allLetDecls
                .dropWhile { PsiUtilCore.compareElementsByPosition(context.place, it) < 0 }
                // Drops at most one element
                .dropWhile { PsiTreeUtil.isAncestor(it, context.place, true) }

        result = visibleLetDecls.flatMap { it.boundElements.scopeEntries } + o.itemEntries(context)
    }

    override fun visitStructItem(o: RustStructItemElement) {
        result = o.typeParamEntries
    }

    override fun visitEnumItem(o: RustEnumItemElement) {
        result = o.enumBody.enumVariantList.scopeEntries + o.typeParamEntries
    }

    override fun visitTraitItem(o: RustTraitItemElement) {
        result = o.typeParamEntries
    }

    override fun visitTypeItem(o: RustTypeItemElement) {
        result = o.typeParamEntries
    }

    override fun visitFnItem(o: RustFnItemElement) {
        visitFunctionLike(o.parameters, o)
    }

    override fun visitTraitMethodMember(o: RustTraitMethodMemberElement) {
        visitFunctionLike(o.parameters, o)
    }

    override fun visitImplMethodMember(o: RustImplMethodMemberElement) {
        visitFunctionLike(o.parameters, o)
    }

    override fun visitImplItem(o: RustImplItemElement) {
        result = o.typeParamEntries
    }

    override fun visitLambdaExpr(o: RustLambdaExprElement) {
        result = o.parameters.parameterList.orEmpty().asSequence()
            .flatMap { it.boundElements.scopeEntries }
    }

    override fun visitMatchArm(o: RustMatchArmElement) {
        result = o.matchPat.boundElements.scopeEntries
    }

    override fun visitWhileLetExpr(o: RustWhileLetExprElement) {
        visitScopedLetDecl(o.scopedLetDecl)
    }

    override fun visitIfLetExpr(o: RustIfLetExprElement) {
        visitScopedLetDecl(o.scopedLetDecl)
    }

    fun visitMod(mod: RustMod) {
        val module = mod.module
        // Rust injects implicit `extern crate std` in every crate root module unless it is
        // a `#![no_std]` crate, in which case `extern crate core` is injected.
        // The stdlib lib itself is `#![no_std]`.
        // We inject both crates for simplicity for now.
        val injectedCrates = if (module == null || !mod.isCrateRoot)
            emptySequence()
        else
            sequenceOf(AutoInjectedCrates.std, AutoInjectedCrates.core).mapNotNull { crateName ->
                ScopeEntryImpl.lazy(crateName) {
                    module.project.getPsiFor(module.cargoProject?.findExternCrateRootByName(crateName))?.rustMod
                }
            }

        // Rust injects implicit `use std::prelude::v1::*` into every module.
        val preludeSymbols = if (module == null || context.inPrelude)
            emptySequence()
        else
            module.preludeModule?.rustMod?.declarations(context.copy(inPrelude = true)) ?: emptySequence()

        result = sequenceOf(
            mod.itemEntries(context),
            injectedCrates,
            preludeSymbols
        ).flatten()
    }

    fun visitFunctionLike(params: RustParametersElement?, fn: RustGenericDeclaration) {
        result = listOfNotNull(params?.selfArgument?.let { ScopeEntryImpl.of(it) }).asSequence() +
            params?.parameterList.orEmpty().asSequence().flatMap { it.boundElements.scopeEntries } +
            fn.typeParamEntries
    }
}

private fun RustItemsOwner.itemEntries(context: Context): Sequence<RustResolveScope.Entry> {
    // wildcard imports have low priority
    val (wildCardImports, usualImports) = useDeclarations.partition { it.isStarImport }
    val imports = (usualImports + wildCardImports).asSequence().flatMap { it.importedEntries(context) }

    return sequenceOf (
        // XXX: this must come before itemList to resolve `Box` from prelude. We need to handle cfg attributes to
        // fix this properly
        modDecls.asSequence().mapNotNull {
            ScopeEntryImpl.lazy(it.name) { it.reference?.resolve() }
        },

        itemList.filter {
            !(it is RustExternCrateItemElement || it is RustUseItemElement || it is RustModDeclItemElement)
        }.scopeEntries,

        externalCrates.asSequence().mapNotNull { externCrate ->
            ScopeEntryImpl.lazy(externCrate.alias?.name ?: externCrate.name) { externCrate.reference?.resolve() }
        },

        imports
    ).flatten()
}

private fun RustUseItemElement.importedEntries(context: Context): Sequence<RustResolveScope.Entry> {
    if (isStarImport) {
        if (this in context.visitedStarImports) return emptySequence()
        // Recursively step into `use foo::*`
        val pathPart = path ?: return emptySequence()
        val mod = pathPart.reference.resolve() as? RustResolveScope ?: return emptySequence()
        return mod.declarations(context.copy(visitedStarImports = context.visitedStarImports + this))
    }

    val globList = useGlobList
    if (globList == null) {
        val path = path ?: return emptySequence()
        // use foo::bar [as baz];
        val entry = ScopeEntryImpl.lazy(alias?.name ?: path.referenceName) { path.reference.resolve() }
        return listOfNotNull(entry).asSequence()
    }

    return globList.useGlobList.asSequence().mapNotNull { glob ->
        val name = listOfNotNull(
            glob.alias?.name, // {foo as bar};
            glob.self?.let { path?.referenceName }, // {self}
            glob.referenceName // {foo}
        ).firstOrNull()

        ScopeEntryImpl.lazy(name) { glob.reference.resolve() }
    }
}

private val Collection<RustNamedElement>.scopeEntries: Sequence<RustResolveScope.Entry>
    get() = asSequence().mapNotNull { ScopeEntryImpl.of(it) }

private val RustGenericDeclaration.typeParamEntries: Sequence<RustResolveScope.Entry>
    get() = genericParams?.typeParamList.orEmpty().scopeEntries
