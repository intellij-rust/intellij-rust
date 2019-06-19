/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.refactoring.RsNamesValidator
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.resolve.ref.RsLifetimeReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsLifetimeStub


val RsLifetime.isPredefined: Boolean get() = referenceName in RsNamesValidator.RESERVED_LIFETIME_NAMES

abstract class RsLifetimeImplMixin : RsStubbedNamedElementImpl<RsLifetimeStub>, RsLifetime {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsLifetimeStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val referenceNameElement: PsiElement get() = nameIdentifier

    override val referenceName: String get() = greenStub?.name ?: referenceNameElement.text

    override fun getReference(): RsReference = RsLifetimeReferenceImpl(this)

    override fun getNameIdentifier(): PsiElement = quoteIdentifier

    override fun setName(name: String): PsiElement? {
        nameIdentifier.replace(RsPsiFactory(project).createQuoteIdentifier(name))
        return this
    }

    override fun getUseScope(): SearchScope = RsPsiImplUtil.getParameterUseScope(this) ?: super.getUseScope()
}

sealed class LifetimeName {
    /** User-given names or fresh (synthetic) names. */
    data class Parameter(val name: String) : LifetimeName()

    /** User typed nothing. e.g. the lifetime in `&u32`. */
    object Implicit : LifetimeName()

    /** User typed `'_`. */
    object Underscore : LifetimeName()

    /** User wrote `'static` */
    object Static : LifetimeName()
}

val LifetimeName.isElided: Boolean
    get() = when (this) {
        LifetimeName.Implicit, LifetimeName.Underscore -> true
        is LifetimeName.Parameter, LifetimeName.Static -> false
    }

val LifetimeName.isStatic: Boolean get() = this == LifetimeName.Static

val RsLifetime?.typedName: LifetimeName
    get() {
        return when (val text = this?.referenceName) {
            null -> LifetimeName.Implicit
            "'_" -> LifetimeName.Underscore
            "'static" -> LifetimeName.Static
            else -> LifetimeName.Parameter(text)
        }
    }

val RsLifetime?.isElided: Boolean get() = typedName.isElided

val RsLifetime?.isStatic: Boolean get() = typedName.isStatic
