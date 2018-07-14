/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.resolve.ref.RsLifetimeReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsLifetimeStub
import org.rust.lang.refactoring.RsNamesValidator


val RsLifetime.isPredefined: Boolean get() = referenceName in RsNamesValidator.PredefinedLifetimes

abstract class RsLifetimeImplMixin : RsStubbedNamedElementImpl<RsLifetimeStub>, RsLifetime {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsLifetimeStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsReference = RsLifetimeReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = quoteIdentifier

    override val referenceName: String get() = stub?.name ?: referenceNameElement.text
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

val RsLifetime?.name: LifetimeName
    get() {
        val text = this?.referenceName
        return when (text) {
            null -> LifetimeName.Implicit
            "'_" -> LifetimeName.Underscore
            "'static" -> LifetimeName.Static
            else -> LifetimeName.Parameter(text)
        }
    }

val RsLifetime?.isElided: Boolean get() = name.isElided

val RsLifetime?.isStatic: Boolean get() = name.isStatic
