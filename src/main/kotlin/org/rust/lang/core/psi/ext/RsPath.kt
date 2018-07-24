/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.resolve.ref.RsPathReference
import org.rust.lang.core.resolve.ref.RsPathReferenceImpl
import org.rust.lang.core.stubs.RsPathStub

val RsPath.hasColonColon: Boolean get() = stub?.hasColonColon ?: (coloncolon != null)
val RsPath.hasCself: Boolean get() = stub?.hasCself ?: (cself != null)

tailrec fun RsPath.basePath(): RsPath {
    val qualifier = path
    @Suppress("IfThenToElvis")
    return if (qualifier == null) this else qualifier.basePath()
}

abstract class RsPathImplMixin : RsStubbedElementImpl<RsPathStub>,
                                 RsPath {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RsPathStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsPathReference = RsPathReferenceImpl(this)

    override val referenceNameElement: PsiElement
        get() = checkNotNull(identifier ?: self ?: `super` ?: cself) {
            "Path must contain identifier: $this ${this.text} at ${this.containingFile.virtualFile.path}"
        }

    override val referenceName: String get() = stub?.referenceName ?: referenceNameElement.text

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)
}
