package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustPathElement
import org.rust.lang.core.psi.RustUseItemElement
import org.rust.lang.core.psi.impl.RustStubbedElementImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RustPathReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.stubs.RustPathElementStub
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.symbols.RustPathSegment

abstract class RustPathImplMixin : RustStubbedElementImpl<RustPathElementStub>,
                                   RustPathElement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RustPathElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RustReference = RustPathReferenceImpl(this)

    override val referenceNameElement: PsiElement
        get() = checkNotNull(identifier ?: self ?: `super` ?: cself) {
            "Path must contain identifier: $this ${this.text} at ${this.containingFile.virtualFile.path}"
        }

    override val referenceName: String get() = stub?.referenceName ?: referenceNameElement.text

    override fun getParent(): PsiElement? = parentByStub
}

val RustPathElement.isCrateRelative: Boolean get() = stub?.isCrateRelative ?: (coloncolon != null)

/**
 * Convert path PSI element into PSI independent representation.
 *
 * Return `null` if the path is malformed. Paths can contain
 * any combination of identifiers and self and super keywords.
 * However, a path is "well formed" only if it starts with
 * `(self::)? (super::)*`.
 *
 * Reference:
 *   https://doc.rust-lang.org/reference.html#paths
 */
val RustPathElement.asRustPath: RustPath? get() {
    val qualifier = path
    val isSelf = referenceName == "self"
    val isSuper = referenceName == "super"

    if (qualifier != null) {
        val qpath = qualifier.asRustPath ?: return null
        if (isSelf) {
            return null // Forbid `foo::self`.
        }

        if (isSuper) {
            return when {
                qpath is RustPath.ModRelative && qpath.segments.isEmpty() ->
                    RustPath.ModRelative((qpath.level + 1), emptyList())
                else -> null // Forbid `foo::super`.
            }
        }

        return qpath.join(segment)
    }

    return when {
        isCrateRelative ->
            if (isSelf || isSuper)
                null // Forbid `::super` and `::self`.
            else
                RustPath.CrateRelative(listOf(segment))

    // `self` can mean two different things:
    //  * if it is a part of a bigger path or a use declaration,
    //    then it is a reference to the current module,
    //  * if it is the only segment of path, then it is an identifier,
        isSelf ->
            when (parent) {
                is RustPathElement, is RustUseItemElement ->
                    RustPath.ModRelative(0, emptyList())
                else -> RustPath.Named(segment)
            }

        isSuper ->
            RustPath.ModRelative(1, emptyList())

    // Paths in use items are implicitly global.
        parentOfType<RustUseItemElement>() != null ->
            RustPath.CrateRelative(listOf(segment))

        else ->
            RustPath.Named(segment)
    }
}

private val RustPathElement.segment: RustPathSegment
    get() = RustPathSegment(referenceName, typeArgumentList?.typeList.orEmpty())
