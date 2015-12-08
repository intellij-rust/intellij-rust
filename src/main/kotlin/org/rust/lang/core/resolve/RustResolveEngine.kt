package org.rust.lang.core.resolve

import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.containingMod
import org.rust.lang.core.psi.util.isBefore
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.util.useDeclarations
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.resolve.util.RustResolveUtil
import java.util.*

object RustResolveEngine {

    open class ResolveResult(elem: RustNamedElement?) : com.intellij.psi.ResolveResult {

        val resolved = elem

        object UNRESOLVED : ResolveResult(null)

        override fun getElement():      RustNamedElement? = resolved
        override fun isValidResult():   Boolean           = resolved != null
    }

    fun resolve(ref: RustQualifiedReferenceElement): ResolveResult =
        Resolver().resolve(ref)

    fun resolveUseGlob(ref: RustUseGlob): ResolveResult =
        Resolver().resolveUseGlob(ref)
}


private val UNRESOLVED = RustResolveEngine.ResolveResult.UNRESOLVED


private class Resolver(private val visitedImports: MutableSet<RustUseItem> = HashSet()) {
    fun resolve(ref: RustQualifiedReferenceElement): RustResolveEngine.ResolveResult {
        val qual = ref.qualifier

        if (qual != null) {
            val parent = if (qual.isModulePrefix) {
                resolveModulePrefix(qual)
            } else {
                resolve(qual).element
            }
            return when (parent) {
                is RustResolveScope -> resolveIn(ResolveScopeVisitor(ref), listOf(parent))
                else                -> UNRESOLVED
            }
        }
        return resolveIn(ResolveScopeVisitor(ref), enumerateScopesFor(ref))
    }

    fun resolveUseGlob(ref: RustUseGlob): RustResolveEngine.ResolveResult {
        val useItem = ref.parentOfType<RustUseItem>()
        val basePath = useItem?.let { it.viewPath.pathPart } ?: return UNRESOLVED

        // this is not necessary a module, e.g.
        //
        //   ```
        //   fn foo() {}
        //
        //   mod inner {
        //       use foo::{self};
        //   }
        //   ```
        val baseItem = resolve(basePath).element

        // `use foo::{self}`
        if (ref.self != null) {
            return RustResolveEngine.ResolveResult(baseItem)
        }

        // `use foo::{bar}`
        val scope = baseItem as? RustResolveScope ?: return UNRESOLVED
        return resolveIn(ResolveScopeVisitor(ref), listOf(scope))
    }

    private fun resolveModulePrefix(ref: RustQualifiedReferenceElement): RustModItem? {
        return if (ref.isSelf) {
            RustResolveUtil.getSelfModFor(ref)
        } else {
            val qual = ref.qualifier
            val mod = if (qual != null) resolveModulePrefix(qual) else ref.containingMod
            RustResolveUtil.getSuperModFor(mod ?: return null)
        }
    }

    private fun resolveIn(v: ResolveScopeVisitor, scopes: Iterable<RustResolveScope>): RustResolveEngine.ResolveResult {
        for (s in scopes) {
            s.resolveWith(v)?.let {
                return RustResolveEngine.ResolveResult(it)
            }
        }

        return UNRESOLVED
    }

    inner class ResolveScopeVisitor(private val name: RustNamedElement) : RustVisitor() {

        var matched: RustNamedElement? = null

        override fun visitModItem(o: RustModItem) {
            seek(o.itemList)

            for (use in o.useDeclarations) {
                if (shouldStop) {
                    return
                }

                processUseDeclaration(use)
            }
        }

        override fun visitForExpr(o: RustForExpr)               = seek(o.scopedForDecl)
        override fun visitScopedLetExpr(o: RustScopedLetExpr)   = visitResolveScope(o)
        override fun visitLambdaExpr(o: RustLambdaExpr)         = visitResolveScope(o)
        override fun visitMethod(o: RustMethod)                 = visitResolveScope(o)
        override fun visitFnItem(o: RustFnItem)                 = visitResolveScope(o)
        override fun visitResolveScope(scope: RustResolveScope) = seek(scope.getDeclarations())

        override fun visitBlock(o: RustBlock) {
            o.getDeclarations()
                .takeWhile { it.isBefore(name) }
                .reversed()
                .forEach { letDecl ->
                    letDecl.getBoundElements().forEach { e ->
                        // defer costly isAncestor checks
                        if (match(e) && !PsiTreeUtil.isAncestor(letDecl, name, true)) {
                            return found(e)
                        }
                    }
                }
        }

        private fun processUseDeclaration(use: RustUseItem) {
            val path = use.viewPath
            val pathPart = path.pathPart ?: return

            val isPlainPathImport = path.mul == null && path.lbrace == null

            // `use foo::bar as baz;`
            if (isPlainPathImport) {
                val name = path.alias ?: pathPart
                if (match(name)) {
                    if (use in visitedImports) {
                        return
                    }
                    visitedImports += use

                    val item = resolve(pathPart).element ?: return
                    return found(item)
                }
            }
        }

        private fun seek(elem: RustDeclaringElement) = seek(listOf(elem))

        private fun seek(decls: Collection<RustDeclaringElement>) {
            decls.flatMap { it.getBoundElements() }
                 .find { match(it) }
                 ?.let { found(it) }
        }

        private fun found(elem: RustNamedElement) {
            matched = elem

            if (elem is RustModDeclItem) {
                elem.reference?.resolve().let {
                    matched = it
                }
            }
        }

        private fun match(elem: RustNamedElement): Boolean =
            name.nameElement?.let { refName ->
                elem.nameElement?.textMatches(refName)
            } ?: false

        private val shouldStop: Boolean
            get() = matched != null
    }
}


private fun enumerateScopesFor(ref: RustQualifiedReferenceElement): Iterable<RustResolveScope> {
    if (ref.isFullyQualified) {
        return listOfNotNull(RustResolveUtil.getCrateRootFor(ref))
    }

    return object : Iterable<RustResolveScope> {
        override fun iterator(): Iterator<RustResolveScope> {
            return object : Iterator<RustResolveScope> {

                private var next = RustResolveUtil.getResolveScopeFor(ref)

                private fun climb(): RustResolveScope {
                    val prev = next!!
                    next = when (prev) {
                        is RustModItem -> null
                        else           -> RustResolveUtil.getResolveScopeFor(prev)
                    }
                    return prev
                }

                override fun next(): RustResolveScope = climb()

                override fun hasNext(): Boolean = next != null
            }
        }
    }
}


private fun RustResolveScope.resolveWith(v: Resolver.ResolveScopeVisitor): RustNamedElement? {
    this.accept(v)
    return v.matched
}
