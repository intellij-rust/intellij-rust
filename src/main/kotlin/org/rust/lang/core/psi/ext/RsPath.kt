/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.RsMacroPathReferenceImpl
import org.rust.lang.core.resolve.ref.RsPathReference
import org.rust.lang.core.resolve.ref.RsPathReferenceImpl
import org.rust.lang.core.stubs.RsPathStub

private val RS_PATH_KINDS = tokenSetOf(IDENTIFIER, SELF, SUPER, CSELF, CRATE)

val RsPath.hasColonColon: Boolean get() = greenStub?.hasColonColon ?: (coloncolon != null)
val RsPath.hasCself: Boolean get() = kind == PathKind.CSELF
val RsPath.kind: PathKind get() {
    val stub = greenStub
    if (stub != null) return stub.kind
    val child = node.findChildByType(RS_PATH_KINDS)
    return when (child?.elementType) {
        IDENTIFIER -> PathKind.IDENTIFIER
        SELF -> PathKind.SELF
        SUPER -> PathKind.SUPER
        CSELF -> PathKind.CSELF
        CRATE -> PathKind.CRATE
        else -> error("Malformed RsPath element: `$text`")
    }
}

/** For `Foo::bar::baz::quux` path returns `Foo` */
tailrec fun RsPath.basePath(): RsPath {
    val qualifier = path
    @Suppress("IfThenToElvis")
    return if (qualifier == null) this else qualifier.basePath()
}

val RsPath.textRangeOfLastSegment: TextRange
    get() = TextRange(referenceNameElement.startOffset, typeArgumentList?.endOffset ?: referenceNameElement.endOffset)

enum class PathKind {
    IDENTIFIER,
    SELF,
    SUPER,
    CSELF,
    CRATE
}

val RsPath.qualifier: RsPath?
    get() {
        path?.let { return it }
        var ctx = context
        while (ctx is RsPath) {
            ctx = ctx.context
        }
        return (ctx as? RsUseSpeck)?.qualifier
    }

fun RsPath.allowedNamespaces(isCompletion: Boolean = false): Set<Namespace> = when (val parent = parent) {
    is RsPath, is RsTypeElement, is RsTraitRef, is RsStructLiteral -> TYPES
    is RsUseSpeck -> when {
        // use foo::bar::{self, baz};
        //     ~~~~~~~~
        // use foo::bar::*;
        //     ~~~~~~~~
        parent.useGroup != null || parent.isStarImport -> TYPES
        // use foo::bar;
        //     ~~~~~~~~
        else -> TYPES_N_VALUES_N_MACROS
    }
    is RsPathExpr -> if (isCompletion) TYPES_N_VALUES else VALUES
    is RsPathCodeFragment -> parent.ns
    else -> TYPES_N_VALUES
}

val RsPath.lifetimeArguments: List<RsLifetime> get() = typeArgumentList?.lifetimeList.orEmpty()

val RsPath.typeArguments: List<RsTypeReference> get() = typeArgumentList?.typeReferenceList.orEmpty()

val RsPath.constArguments: List<RsExpr> get() = typeArgumentList?.exprList.orEmpty()

abstract class RsPathImplMixin : RsStubbedElementImpl<RsPathStub>,
                                 RsPath {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RsPathStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsPathReference =
        if (parent is RsMacroCall) RsMacroPathReferenceImpl(this) else RsPathReferenceImpl(this)

    override val referenceNameElement: PsiElement
        get() = checkNotNull(identifier ?: self ?: `super` ?: cself ?: crate) {
            "Path must contain identifier: $this ${this.text} at ${this.containingFile.virtualFile.path}"
        }

    override val referenceName: String get() = greenStub?.referenceName ?: super.referenceName

    override val containingMod: RsMod
        get() {
            // In the case of path inside vis restriction for mod item, containingMod must be the parent module:
            // ```
            // mod foo {
            //     pub(in self) mod bar {}
            //          //^ containingMod == `foo`
            // ```
            val visParent = contextStrict<RsVis>()?.context
            return if (visParent is RsMod) visParent.containingMod else super.containingMod
        }
}
