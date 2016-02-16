package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import org.rust.cargo.project.module.util.crateRoots
import org.rust.lang.core.names.RustAnonymousId
import org.rust.lang.core.names.RustFileModuleId
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFileImpl
import org.rust.lang.core.psi.impl.mixin.basePath
import org.rust.lang.core.psi.impl.mixin.letDeclarationsVisibleAt
import org.rust.lang.core.psi.util.*
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.resolve.util.RustResolveUtil
import java.util.*

object RustResolveEngine {
    open class ResolveResult private constructor(val resolved: RustNamedElement?) : com.intellij.psi.ResolveResult {
        override fun getElement():      RustNamedElement? = resolved
        override fun isValidResult():   Boolean           = resolved != null

        /**
         * Designates resolve-engine failure to properly resolve item
         */
        object Unresolved : ResolveResult(null)

        /**
         * Designates resolve-engine failure to properly recognise target item
         * among the possible candidates
         */
        class Ambiguous(val candidates: Collection<RustNamedElement>) : ResolveResult(null)

        /**
         * Designates resolve-engine successfully resolved given target
         */
        class Resolved(resolved: RustNamedElement) : ResolveResult(resolved)
    }

    /**
     * Resolves abstract `qualified-names`
     *
     * NOTE: Those names are treated as implicitly _fully-qualified_ once
     *       therefore none of them may contain `super`, `self` references
     */
    fun resolve(name: RustQualifiedName, module: Module): ResolveResult =
        module.crateRoots
              .map { Resolver().resolve(name, it) }
              .firstOrNull { it.isValidResult }
              ?: ResolveResult.Unresolved

    /**
     * Resolves `qualified-reference` bearing PSI-elements
     *
     * NOTE: This operate on PSI to extract all the necessary (yet implicit) resolving-context
     */
    fun resolve(ref: RustQualifiedReferenceElement): ResolveResult =
        Resolver().resolve(ref)

    //
    // TODO(kudinkin): Unify following?
    //

    fun resolveModDecl(ref: RustModDeclItem): ResolveResult =
        Resolver().resolveModDecl(ref)

    fun resolveUseGlob(ref: RustUseGlob): ResolveResult =
        Resolver().resolveUseGlob(ref)

}


private class Resolver {

    private val seen: MutableSet<RustNamedElement> = HashSet()

    /**
     * Resolves abstract qualified-names
     *
     * For more details check out `RustResolveEngine.resolve`
     */
    fun resolve(name: RustQualifiedName, root: RustModItem): RustResolveEngine.ResolveResult {
        if (name == RustAnonymousId) {
            return RustResolveEngine.ResolveResult.Resolved(root)
        } else if (name is RustFileModuleId) {
            return name.path.findModuleIn(root.project)?.let {
                RustResolveEngine.ResolveResult.Resolved(it)
            } ?: RustResolveEngine.ResolveResult.Unresolved
        }

        return resolve(name.qualifier!!, root).element?.let {
            when (it) {
                is RustResolveScope -> resolveIn(sequenceOf(it), by(name))
                else                -> null
            }
        } ?: RustResolveEngine.ResolveResult.Unresolved
    }

    /**
     * Resolves `qualified-reference` bearing PSI-elements
     *
     * For more details check out `RustResolveEngine.resolve`
     */
    fun resolve(ref: RustQualifiedReferenceElement): RustResolveEngine.ResolveResult {
        val qual = ref.qualifier
        if (qual != null) {
            val parent = if (qual.isModulePrefix) {
                resolveModulePrefix(qual)
            } else {
                resolve(qual).element
            }

            return when (parent) {
                is RustResolveScope -> resolveIn(sequenceOf(parent), by(ref))
                else                -> RustResolveEngine.ResolveResult.Unresolved
            }
        }

        return resolveIn(enumerateScopesFor(ref), by(ref))
    }

    /**
     * Looks-up file corresponding to particular module designated by `mod-declaration-item`:
     *
     *  ```
     *  // foo.rs
     *  pub mod bar; // looks up `bar.rs` or `bar/mod.rs` in the same dir
     *
     *  pub mod nested {
     *      pub mod baz; // looks up `nested/baz.rs` or `nested/baz/mod.rs`
     *  }
     *
     *  ```
     *
     *  | A module without a body is loaded from an external file, by default with the same name as the module,
     *  | plus the '.rs' extension. When a nested sub-module is loaded from an external file, it is loaded
     *  | from a subdirectory path that mirrors the module hierarchy.
     *
     * Reference:
     *      https://github.com/rust-lang/rust/blob/master/src/doc/reference.md#modules
     */
    fun resolveModDecl(ref: RustModDeclItem): RustResolveEngine.ResolveResult {
        val parent  = ref.containingMod
        val name    = ref.name

        if (parent == null || name == null || !parent.ownsDirectory) {
            return RustResolveEngine.ResolveResult.Unresolved
        }

        val dir = parent.ownedDirectory

        // Lookup `name.rs` module
        val fileName = "$name.rs"
        val fileMod  = dir?.findFile(fileName) as? RustFileImpl

        // Lookup `name/mod.rs` module
        val dirMod = dir?.findSubdirectory(name)?.findFile(RustModules.MOD_RS) as? RustFileImpl

        val resolved = listOf(fileMod, dirMod).mapNotNull { it?.mod }

        return when (resolved.size) {
            0    -> RustResolveEngine.ResolveResult.Unresolved
            1    -> RustResolveEngine.ResolveResult.Resolved    (resolved.single())
            else -> RustResolveEngine.ResolveResult.Ambiguous   (resolved)
        }
    }

    /**
     * Resolves `use-glob`s, ie:
     *
     *  ```
     *  use foo::bar::{baz as boo}
     *  use foo::*
     *  ```
     */
    fun resolveUseGlob(ref: RustUseGlob): RustResolveEngine.ResolveResult {
        val basePath = ref.basePath ?: return RustResolveEngine.ResolveResult.Unresolved

        //
        // This is not necessarily a module, e.g.
        //
        //   ```
        //   fn foo() {}
        //
        //   mod inner {
        //       use foo::{self};
        //   }
        //   ```
        //
        val baseItem = resolve(basePath).element

        // `use foo::{self}`
        if (ref.self != null && baseItem != null) {
            return RustResolveEngine.ResolveResult.Resolved(baseItem)
        }

        // `use foo::{bar}`
        val scope = baseItem as? RustResolveScope ?: return RustResolveEngine.ResolveResult.Unresolved
        return resolveIn(sequenceOf(scope), by(ref))
    }

    private fun resolveModulePrefix(ref: RustQualifiedReferenceElement): RustModItem? {
        return if (ref.isSelf) {
            ref.containingMod
        } else {
            val qual = ref.qualifier
            val mod = if (qual != null) resolveModulePrefix(qual) else ref.containingMod
            mod?.`super`
        }
    }

    /**
     * Hook to compose non-local resolving-context to resolve (ie module-level) _items_ in a
     * context-free manner: it's essentially just scraping the whole scope seeking for the
     * given name
     *
     * @name name to be sought after
     */
    private fun by(name: RustQualifiedName) =
        ResolveContext.Companion.Trivial(ResolveNonLocalScopesVisitor(name.part.identifier))

    /**
     * Hook to compose _total_ (ie including both local & non-local) context to resolve
     * any items, taking into account what lexical point we're particularly looking that name up from,
     * therefore effectively ignoring items being declared 'lexically-after' lookup-point
     */
    private fun by(e: RustNamedElement) =
        e.name?.let {
            ResolveContext.Companion.Trivial(ResolveLocalScopesVisitor(e))
        } ?: ResolveContext.Companion.Empty

    /**
     * Resolve-context wrapper
     */
    interface ResolveContext {
        fun accept(scope: RustResolveScope): RustNamedElement?

        companion object {

            class Trivial(val v: ResolveScopeVisitor) : ResolveContext {
                override fun accept(scope: RustResolveScope): RustNamedElement? {
                    scope.accept(v)
                    return v.matched
                }
            }

            object Empty : ResolveContext {
                override fun accept(scope: RustResolveScope): RustNamedElement? = null
            }
        }
    }



    private fun resolveIn(scopes: Sequence<RustResolveScope>, ctx: ResolveContext): RustResolveEngine.ResolveResult {
        for (s in scopes) {
            s.resolveUsing(ctx)?.let {
                return RustResolveEngine.ResolveResult.Resolved(it)
            }
        }

        return RustResolveEngine.ResolveResult.Unresolved
    }

    /**
     * Abstract resolving-scope-visitor
     */
    abstract inner class ResolveScopeVisitor : RustVisitor() {

        /**
         * Matched resolve-target
         */
        abstract var matched: RustNamedElement?
    }

    /**
     * This particular visitor visits _non-local_ scopes only (!)
     */
    open inner class ResolveNonLocalScopesVisitor(protected val name: String) : ResolveScopeVisitor() {

        override var matched: RustNamedElement? = null

        override fun visitModItem(o: RustModItem) {
            seek(o.itemList)
        }

        private fun addToSeen(element: RustNamedElement): Boolean {
            val result = element in seen
            seen += element
            return result
        }

        protected fun seek(elem: RustDeclaringElement) = seek(listOf(elem))

        protected fun seek(decls: Collection<RustDeclaringElement>) {
            decls.flatMap { it.boundElements }
                .find { match(it) }
                ?.let { found(it) }
        }

        protected fun found(elem: RustNamedElement) {
            matched =
                when (elem) {
                    // mod-decl-items constitute a (sub-) _tree_
                    // inside the crate's module-tree, therefore
                    // there is no need to mark them as seen
                    is RustModDeclItem ->
                        elem.reference?.let { it.resolve() }

                    // Check whether resolved element (being path-part, use-glob, or alias)
                    // could be further resolved

                    is RustPathPart ->
                        if (!addToSeen(elem))   resolve(elem).element
                        else                    null

                    is RustUseGlob ->
                        if (!addToSeen(elem))   resolveUseGlob(elem).element
                        else                    null

                    is RustAlias -> {
                        val parent = elem.parent
                        when (parent) {

                            is RustViewPath ->
                                parent.pathPart?.let {
                                    if (!addToSeen(it)) resolve(it).element
                                    else                null
                                }


                            is RustUseGlob ->
                                if (!addToSeen(parent)) resolveUseGlob(parent).element
                                else                    null

                            else -> elem
                        }
                    }

                    else -> elem
                }
        }

        protected fun match(elem: RustNamedElement): Boolean =
            elem.nameElement?.textMatches(name) ?: false
    }

    /**
     * This particular visitor traverses both local & non-local scopes
     */
    inner class ResolveLocalScopesVisitor(ref: RustNamedElement) : ResolveNonLocalScopesVisitor(ref.name!!) {

        private val context: RustCompositeElement = ref

        override fun visitForExpr       (o: RustForExpr)            = seek(o.scopedForDecl)
        override fun visitScopedLetExpr (o: RustScopedLetExpr)      = visitResolveScope(o)
        override fun visitLambdaExpr    (o: RustLambdaExpr)         = visitResolveScope(o)
        override fun visitTraitMethod   (o: RustTraitMethod)        = visitResolveScope(o)
        override fun visitFnItem        (o: RustFnItem)             = visitResolveScope(o)
        override fun visitResolveScope  (scope: RustResolveScope)   = seek(scope.declarations)

        override fun visitBlock(o: RustBlock) {
            o.letDeclarationsVisibleAt(context)
                .flatMap { it.boundElements.asSequence() }
                .filter { match(it) }
                .firstOrNull()
                ?.let { found(it) }
        }
    }
}


fun enumerateScopesFor(ref: RustQualifiedReferenceElement): Sequence<RustResolveScope> {
    if (ref.isFullyQualified) {
        return listOfNotNull(RustResolveUtil.getCrateRootModFor(ref)).asSequence()
    }

    return generateSequence(RustResolveUtil.getResolveScopeFor(ref)) { parent ->
        when (parent) {
            is RustModItem  -> null
            else            -> RustResolveUtil.getResolveScopeFor(parent)
        }
    }
}


private fun RustResolveScope.resolveUsing(c: Resolver.ResolveContext): RustNamedElement? = c.accept(this)
