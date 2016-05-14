package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.core.resolve.ref.RustQualifiedReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference

abstract class RustPathImplMixin(node: ASTNode) : RustNamedElementImpl(node)
                                                , RustQualifiedReferenceElement
                                                , RustPath {

    override fun getReference(): RustReference = RustQualifiedReferenceImpl(this)

    override val nameElement: PsiElement?
        get() = identifier ?: self ?: `super`

    override val qualifier: RustQualifiedReferenceElement? get() = path

    private val isViewPath: Boolean get() {
        val parent = parent
        return parent is RustUseItem || (parent is RustPathImplMixin && parent.isViewPath)
    }

    override val isRelativeToCrateRoot: Boolean
        get() {
            val qual = qualifier
            return if (qual == null) {
                separator != null || (isViewPath && self == null && `super` == null)
            } else {
                qual.isRelativeToCrateRoot
            }
        }

    override val relativeModulePrefix: RelativeModulePrefix get() {
        val qual = qualifier
        val isSelf = self != null
        val isSuper = `super` != null
        check(!(isSelf && isSuper))

        if (qual != null) {
            if (isSelf) return RelativeModulePrefix.Invalid

            val parent = qual.relativeModulePrefix
            return when (parent) {
                is RelativeModulePrefix.Invalid        -> RelativeModulePrefix.Invalid
                is RelativeModulePrefix.NotRelative    -> when {
                    isSuper -> RelativeModulePrefix.Invalid
                    else    -> RelativeModulePrefix.NotRelative
                }
                is RelativeModulePrefix.AncestorModule -> when {
                    isSuper -> RelativeModulePrefix.AncestorModule(parent.level + 1)
                    else    -> RelativeModulePrefix.NotRelative
                }
            }
        }

        val isFullyQualified = separator != null
        if (isFullyQualified) {
            return if (isSelf || isSuper)
                RelativeModulePrefix.Invalid
            else
                RelativeModulePrefix.NotRelative
        }

        return when {
            // `self` by itself is not a module prefix, it's an identifier.
            // So for `self` we need to check that it's not the only segment of path.
            isSelf && nextSibling != null -> RelativeModulePrefix.AncestorModule(0)
            isSuper -> RelativeModulePrefix.AncestorModule(1)
            else -> RelativeModulePrefix.NotRelative
        }
    }

    override val isSelf: Boolean
        get() = self != null

    private val separator: PsiElement?
        get() = findChildByType(RustTokenElementTypes.COLONCOLON)

}

