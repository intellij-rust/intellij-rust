package org.rust.lang.core.psi.impl.mixin


import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustBlock
import org.rust.lang.core.psi.RustModDeclItem
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RustModReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.stubs.RustItemStub
import java.io.File

abstract class RustModDeclItemImplMixin : RustItemImpl
                                        , RustModDeclItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)


    override fun getReference(): RustReference = RustModReferenceImpl(this)
}

fun RustModDeclItem.getOrCreateModuleFile(): PsiFile? {
    return  reference!!.resolve()?.let { it.containingFile } ?:
            containingMod?.ownedDirectory?.createFile(suggestChildFileName ?: return null)
}

/*
 * A list of relative paths to where this module can be found.
 *
 * Paths are relative to the containing mod directory.
 *
 * Can be of length 0, 1 or 2.
 */
val RustModDeclItem.possiblePaths: List<String> get() {
    explicitPath?.let { return listOf(it) }
    return implicitPaths
}

/*
 * mods inside blocks require explicit path  attribute
 * https://github.com/rust-lang/rust/pull/31534
 */
val RustModDeclItem.isPathAttributeRequired: Boolean get() =
    parentOfType<RustBlock>() != null


//TODO: use explicit path if present.
private val RustModDeclItem.suggestChildFileName: String?
    get() = implicitPaths.firstOrNull()


private val RustModDeclItem.implicitPaths: List<String> get() {
    val name = name ?: return emptyList()
    if (isPathAttributeRequired) {
        return emptyList()
    }
    return listOf("$name.rs", "$name/mod.rs")
}


val RustModDeclItem.explicitPath: String? get() {
    val pathAttr = queryAttributes.lookupStringValueForKey("path") ?: return null
    return if (!File(pathAttr).isAbsolute)
        pathAttr
    else
        null
}
