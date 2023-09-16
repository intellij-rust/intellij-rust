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
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.stubs.RsMacro2Stub
import org.rust.stdext.HashCode
import javax.swing.Icon

abstract class RsMacro2ImplMixin : RsStubbedNamedElementImpl<RsMacro2Stub>,
                                   RsMacro2 {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsMacro2Stub, elementType: IStubElementType<*, *>) : super(stub, elementType)

    override fun getIcon(flags: Int): Icon = iconWithVisibility(flags, RsIcons.MACRO2)

    override val crateRelativePath: String? get() = RsPsiImplUtil.crateRelativePath(this)

    override val modificationTracker: SimpleModificationTracker =
        SimpleModificationTracker()

    override fun incModificationCount(element: PsiElement): Boolean {
        modificationTracker.incModificationCount()
        return false // force rustStructureModificationTracker to be incremented
    }

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)

    override val macroBodyStubbed: RsMacroBody?
        get() {
            return CachedValuesManager.getCachedValue(this) {
                val text = stub?.macroBody ?: prepareMacroBody()
                CachedValueProvider.Result.create(
                    RsPsiFactory(project, markGenerated = false).createMacroBody(text),
                    modificationTracker
                )
            }
        }

    override val bodyHash: HashCode?
        get() {
            val stub = greenStub
            if (stub !== null) return stub.bodyHash
            return CachedValuesManager.getCachedValue(this) {
                val body = prepareMacroBody()
                val hash = HashCode.compute(body)
                CachedValueProvider.Result.create(hash, modificationTracker)
            }
        }

    override val hasRustcBuiltinMacro: Boolean
        get() = MACRO2_HAS_RUSTC_BUILTIN_MACRO_PROP.getByPsi(this)

    override val preferredBraces: MacroBraces
        get() = stub?.preferredBraces ?: guessPreferredBraces()
}

val MACRO2_HAS_RUSTC_BUILTIN_MACRO_PROP: StubbedAttributeProperty<RsMacro2, RsMacro2Stub> =
    StubbedAttributeProperty(QueryAttributes<*>::hasRustcBuiltinMacro, RsMacro2Stub::mayHaveRustcBuiltinMacro)

fun RsMacro2.prepareMacroBody(): String {
    val macroPatternContents = macroPatternContents
    val macroExpansionContents = macroExpansionContents
    return if (macroPatternContents != null && macroExpansionContents != null) {
        "{(${macroPatternContents.text}) => {${macroExpansionContents.text}}}"
    } else {
        macroCaseList.joinToString(prefix = "{", postfix = "}", separator = "") { it.text }
    }
}
