/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.rust.ide.presentation.getPresentation
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.escapeIdentifierIfNeeded
import org.rust.lang.core.psi.unescapedText
import org.rust.lang.core.stubs.RsNamedStub

interface RsNamedElement : RsElement, PsiNamedElement, NavigatablePsiElement

interface RsNameIdentifierOwner : RsNamedElement, PsiNameIdentifierOwner

val RsNamedElement.escapedName: String? get() = name?.escapeIdentifierIfNeeded()

abstract class RsNamedElementImpl(type: IElementType) : RsElementImpl(type),
                                                        RsNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? = findChildByType(IDENTIFIER)?.psi

    override fun getName(): String? = nameIdentifier?.unescapedText

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RsPsiFactory(project).createIdentifier(name))
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}

abstract class RsStubbedNamedElementImpl<StubT> : RsStubbedElementImpl<StubT>,
                                                  RsNameIdentifierOwner
where StubT : RsNamedStub, StubT : StubElement<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findChildByType(IDENTIFIER)

    override fun getName(): String? {
        val stub = greenStub
        return if (stub !== null) stub.name else nameIdentifier?.unescapedText
    }

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RsPsiFactory(project).createIdentifier(name))
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}
