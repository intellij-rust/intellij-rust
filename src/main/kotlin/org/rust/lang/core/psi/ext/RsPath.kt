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
import org.rust.lang.core.completion.RsCommonCompletionProvider
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.*
import org.rust.lang.core.stubs.RsPathStub
import org.rust.lang.core.stubs.common.RsPathPsiOrStub
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.doc.psi.RsDocPathLinkParent

private val RS_PATH_KINDS = tokenSetOf(IDENTIFIER, SELF, SUPER, CSELF, CRATE)

val RsPath.hasCself: Boolean get() = kind == PathKind.CSELF

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

val RsPath.isInsideDocLink: Boolean
    get() = when (val parent = rootPath().parent) {
        is RsDocPathLinkParent -> true
        is RsTypeReference -> parent.ancestorStrict<RsPath>()?.isInsideDocLink ?: false
        else -> false
    }

fun RsPath.allowedNamespaces(isCompletion: Boolean = false, parent: PsiElement? = this.parent): Set<Namespace> = when (parent) {
    is RsPath, is RsTraitRef, is RsStructLiteral, is RsPatStruct -> TYPES
    is RsTypeReference -> when (parent.stubParent) {
        is RsTypeArgumentList -> when {
            // type A = Foo<T>
            //              ~ `T` can be either type or const argument
            typeArgumentList == null && valueParameterList == null -> TYPES_N_VALUES
            else -> TYPES
        }
        else -> TYPES
    }
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
    is RsPathExpr -> when {
        isCompletion && qualifier != null -> TYPES_N_VALUES_N_MACROS
        /** unqualified macros are special cased in [RsCommonCompletionProvider.processPathVariants] */
        isCompletion && qualifier == null -> TYPES_N_VALUES
        else -> VALUES
    }
    is RsPatTupleStruct -> if (isCompletion) TYPES_N_VALUES else VALUES
    is RsMacroCall -> MACROS
    is RsPathCodeFragment -> parent.ns
    // TODO: Use proper namespace based on disambiguator
    is RsDocPathLinkParent -> TYPES_N_VALUES_N_MACROS
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
            is RsPath -> {
                val rootPathParent = parent.rootPath().parent
                if (rootPathParent !is RsMetaItem || RsPsiPattern.nonStdOuterAttributeMetaItem.accepts(rootPathParent)) {
                    RsPathReferenceImpl(this)
                } else {
                    null
                }
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
            return if (visParent is RsMod) visParent.containingMod else super<RsStubbedElementImpl>.containingMod
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
