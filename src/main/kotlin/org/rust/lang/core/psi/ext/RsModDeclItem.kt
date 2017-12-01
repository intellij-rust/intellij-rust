/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext


import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.macros.ExpansionResult
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.resolve.ref.RsModReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsModDeclItemStub
import javax.swing.Icon

fun RsModDeclItem.getOrCreateModuleFile(): PsiFile? {
    val existing = reference.resolve()?.containingFile
    if (existing != null) return existing
    return suggestChildFileName?.let { containingMod.ownedDirectory?.createFile(it) }
}

val RsModDeclItem.isLocal: Boolean
    get() = stub?.isLocal ?: (ancestorStrict<RsBlock>() != null)


//TODO: use explicit path if present.
private val RsModDeclItem.suggestChildFileName: String?
    get() = implicitPaths.firstOrNull()


private val RsModDeclItem.implicitPaths: List<String> get() {
    val name = name ?: return emptyList()
    return if (isLocal) emptyList() else listOf("$name.rs", "$name/mod.rs")
}

val RsModDeclItem.pathAttribute: String? get() = queryAttributes.lookupStringValueForKey("path")

abstract class RsModDeclItemImplMixin : RsStubbedNamedElementImpl<RsModDeclItemStub>,
                                        RsModDeclItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsModDeclItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsReference = RsModReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier

    override val referenceName: String get() = name!!

    override fun getIcon(flags: Int): Icon? = iconWithVisibility(flags, RsIcons.MODULE)

    override val isPublic: Boolean get() = RsPsiImplUtil.isPublic(this, stub)

    override fun getContext() = ExpansionResult.getContextImpl(this)
}

val RsModDeclItem.hasMacroUse: Boolean get() =
    queryAttributes.hasAttribute("macro_use")
