/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroBody
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.stubs.RsMacroStub
import org.rust.stdext.HashCode
import javax.swing.Icon

abstract class RsMacroImplMixin : RsStubbedNamedElementImpl<RsMacroStub>,
                                  RsMacro {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsMacroStub, elementType: IStubElementType<*, *>) : super(stub, elementType)

    override fun getNameIdentifier(): PsiElement? =
        findChildrenByType<PsiElement>(RsElementTypes.IDENTIFIER)
            .getOrNull(1) // Zeroth is `macro_rules` itself

    override fun getIcon(flags: Int): Icon? = RsIcons.MACRO

    override val crateRelativePath: String? get() = name?.let { "::$it" }

    override val modificationTracker: SimpleModificationTracker =
        SimpleModificationTracker()

    override fun incModificationCount(element: PsiElement): Boolean {
        modificationTracker.incModificationCount()
        return false // force rustStructureModificationTracker to be incremented
    }
}

val RsMacro.hasMacroExport: Boolean
    get() = queryAttributes.hasAttribute("macro_export")

val RsMacro.isRustcDocOnlyMacro: Boolean
    get() = queryAttributes.hasAttribute("rustc_doc_only_macro")

val RsMacro.macroBodyStubbed: RsMacroBody?
    get() {
        val stub = stub ?: return macroBody
        val text = stub.macroBody ?: return null
        return CachedValuesManager.getCachedValue(this) {
            CachedValueProvider.Result.create(
                RsPsiFactory(project, markGenerated = false).createMacroBody(text),
                modificationTracker
            )
        }
    }

val RsMacro.bodyHash: HashCode?
    get() = CachedValuesManager.getCachedValue(this) {
        val body = stub?.macroBody ?: macroBody?.text
        val hash = body?.let { HashCode.compute(it) }
        CachedValueProvider.Result.create(hash, modificationTracker)
    }
