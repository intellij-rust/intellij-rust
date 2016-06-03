package org.rust.lang.core.psi.impl.mixin


import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustItemElementImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RustModReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.stubs.RustItemStub
import java.io.File
import javax.swing.Icon

abstract class RustModDeclItemImplMixin : RustItemElementImpl
                                        , RustModDeclItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? {
        return iconWithVisibility(flags, RustIcons.MODULE)
    }

    override fun getReference(): RustReference = RustModReferenceImpl(this)
}

fun RustModDeclItemElement.getOrCreateModuleFile(): PsiFile? {
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
val RustModDeclItemElement.possiblePaths: List<String> get() {
    explicitPath?.let { return listOf(it) }
    return implicitPaths
}

/*
 * mods inside blocks require explicit path  attribute
 * https://github.com/rust-lang/rust/pull/31534
 */
val RustModDeclItemElement.isPathAttributeRequired: Boolean get() =
    parentOfType<RustBlockElement>() != null


//TODO: use explicit path if present.
private val RustModDeclItemElement.suggestChildFileName: String?
    get() = implicitPaths.firstOrNull()


private val RustModDeclItemElement.implicitPaths: List<String> get() {
    val name = name ?: return emptyList()
    if (isPathAttributeRequired) {
        return emptyList()
    }
    return listOf("$name.rs", "$name/mod.rs")
}


val RustModDeclItemElement.explicitPath: String? get() {
    val pathAttr = queryAttributes.lookupStringValueForKey("path") ?: return null
    return if (!File(pathAttr).isAbsolute)
        pathAttr
    else
        null
}
