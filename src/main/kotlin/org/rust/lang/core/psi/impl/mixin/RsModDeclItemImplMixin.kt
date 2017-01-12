package org.rust.lang.core.psi.impl.mixin


import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RsStubbedNamedElementImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RsModReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsModDeclItemStub
import java.io.File
import javax.swing.Icon

abstract class RsModDeclItemImplMixin : RsStubbedNamedElementImpl<RsModDeclItemStub>,
                                        RsModDeclItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsModDeclItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsReference = RsModReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier

    override val referenceName: String get() = referenceNameElement.text

    override fun getIcon(flags: Int): Icon? = iconWithVisibility(flags, RsIcons.MODULE)

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)

    override fun getParent(): PsiElement? {
        val stub = stub
        return if (stub == null || stub.isLocal) parentByTree else parentByStub
    }
}

fun RsModDeclItem.getOrCreateModuleFile(): PsiFile? {
    val existing = reference.resolve()?.containingFile
    if (existing != null) return existing
    return suggestChildFileName?.let { containingMod.ownedDirectory?.createFile(it) }
}

/*
 * A list of relative paths to where this module can be found.
 *
 * Paths are relative to the containing mod directory.
 *
 * Can be of length 0, 1 or 2.
 */
val RsModDeclItem.possiblePaths: List<String> get() {
    val path = pathAttribute
    return if (path != null)
        if (!File(path).isAbsolute) listOf(path) else emptyList()
    else
        implicitPaths
}

val RsModDeclItem.isLocal: Boolean
    get() = stub?.isLocal ?: (parentOfType<RsBlock>() != null)


//TODO: use explicit path if present.
private val RsModDeclItem.suggestChildFileName: String?
    get() = implicitPaths.firstOrNull()


private val RsModDeclItem.implicitPaths: List<String> get() {
    val name = name ?: return emptyList()
    return if (isLocal) emptyList() else listOf("$name.rs", "$name/mod.rs")
}


val RsModDeclItem.pathAttribute: String? get() {
    val stub = stub
    return if (stub != null)
        stub.pathAttribute
    else
        queryAttributes.lookupStringValueForKey("path")
}
