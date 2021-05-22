/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.*
import org.rust.lang.core.stubs.RsPathStub
import org.rust.lang.core.stubs.common.RsPathPsiOrStub
import org.rust.lang.core.types.ty.TyPrimitive

private val RS_PATH_KINDS = tokenSetOf(IDENTIFIER, SELF, SUPER, CSELF, CRATE)

val RsPath.hasCself: Boolean get() = kind == PathKind.CSELF

/** For `Foo::bar::baz::quux` path returns `Foo` */
// TODO remove
tailrec fun RsPath.basePath(): RsPath {
    val qualifier = path
    return if (qualifier === null) this else qualifier.basePath()
}

/** For `Foo::bar::baz::quux` path returns `Foo` */
tailrec fun <T : RsPathPsiOrStub> T.basePath(): T {
    @Suppress("UNCHECKED_CAST")
    val qualifier = path as T?
    return if (qualifier === null) this else qualifier.basePath()
}

/** For `Foo::bar` in `Foo::bar::baz::quux` returns `Foo::bar::baz::quux` */
tailrec fun RsPath.rootPath(): RsPath {
    // Use `parent` instead of `context` because of better performance.
    // Assume nobody set a context for a part of a path
    val parent = parent
    return if (parent is RsPath) parent.rootPath() else this
}

val RsPath.textRangeOfLastSegment: TextRange?
    get() {
        val referenceNameElement = referenceNameElement ?: return null
        return TextRange(
            referenceNameElement.startOffset, typeArgumentList?.endOffset ?: referenceNameElement.endOffset
        )
    }

enum class PathKind {
    IDENTIFIER,
    SELF,
    SUPER,
    CSELF,
    CRATE,
    MALFORMED
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
    is RsPath, is RsTypeReference, is RsTraitRef, is RsStructLiteral, is RsPatStruct -> TYPES
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
    is RsPatTupleStruct -> VALUES
    is RsPathCodeFragment -> parent.ns
    else -> TYPES_N_VALUES
}

val RsPath.resolveStatus: PathResolveStatus
    get() {
        val reference = reference ?: return PathResolveStatus.NO_REFERENCE
        return if (TyPrimitive.fromPath(this) == null && reference.multiResolve().isEmpty()) {
            PathResolveStatus.UNRESOLVED
        } else {
            PathResolveStatus.RESOLVED
        }
    }

enum class PathResolveStatus {
    RESOLVED, UNRESOLVED, NO_REFERENCE
}

val RsPath.lifetimeArguments: List<RsLifetime> get() = typeArgumentList?.lifetimeList.orEmpty()

val RsPath.typeArguments: List<RsTypeReference> get() = typeArgumentList?.typeReferenceList.orEmpty()

val RsPath.constArguments: List<RsExpr> get() = typeArgumentList?.exprList.orEmpty()

abstract class RsPathImplMixin : RsStubbedElementImpl<RsPathStub>,
                                 RsPath {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RsPathStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsPathReference? {
        if (referenceName == null) return null
        return when (val parent = parent) {
            is RsMacroCall -> RsMacroPathReferenceImpl(this)
            is RsMetaItem -> when {
                RsPsiPattern.derivedTraitMetaItem.accepts(parent) -> RsDeriveTraitReferenceImpl(this)
                RsPsiPattern.nonStdOuterAttributeMetaItem.accepts(parent) -> RsAttributeProcMacroReferenceImpl(this)
                else -> null
            }
            else -> RsPathReferenceImpl(this)
        }
    }

    override val referenceNameElement: PsiElement?
        get() = node.findChildByType(RS_PATH_KINDS)?.psi

    override val referenceName: String?
        get() {
            val stub = greenStub
            return if (stub != null) {
                stub.referenceName
            } else {
                super.referenceName
            }
        }

    override val containingMod: RsMod
        get() {
            // In the case of path inside vis restriction for mod item, containingMod must be the parent module:
            // ```
            // mod foo {
            //     pub(in self) mod bar {}
            //          //^ containingMod == `foo`
            // ```
            val visParent = (rootPath().parent as? RsVisRestriction)?.parent?.parent
            return if (visParent is RsMod) visParent.containingMod else super.containingMod
        }

    override val hasColonColon: Boolean get() = greenStub?.hasColonColon ?: (coloncolon != null)

    override val kind: PathKind
        get() {
            val stub = greenStub
            if (stub != null) return stub.kind
            val child = node.findChildByType(RS_PATH_KINDS)
            return when (child?.elementType) {
                IDENTIFIER -> PathKind.IDENTIFIER
                SELF -> PathKind.SELF
                SUPER -> PathKind.SUPER
                CSELF -> PathKind.CSELF
                CRATE -> PathKind.CRATE
                else -> PathKind.MALFORMED
            }
        }
}
