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
