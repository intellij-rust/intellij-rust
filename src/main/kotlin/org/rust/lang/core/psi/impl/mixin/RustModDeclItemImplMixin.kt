package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.psi.util.containingMod
import org.rust.lang.core.psi.util.ownedDirectory


import org.rust.lang.core.resolve.ref.RustModReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.stubs.RustItemStub

abstract class RustModDeclItemImplMixin : RustItemImpl
                                        , RustModDeclItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)


    override fun getReference(): RustReference = RustModReferenceImpl(this)
}

fun RustModDeclItem.getOrCreateModuleFile(): PsiFile? {
    return  reference!!.resolve()?.let { it.containingFile } ?:
            containingMod?.ownedDirectory?.createFile(childFileName ?: return null)
}

private val RustModDeclItem.childFileName: String?
    get() = name?.let { "$it.rs" }
