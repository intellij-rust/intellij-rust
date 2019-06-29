/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightVirtualFile
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.parser.RustParserUtil.PathParsingMode
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsInferenceContextOwner
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.resolve.Namespace

abstract class RsCodeFragment(
    fileViewProvider: FileViewProvider,
    contentElementType: IElementType,
    private val context: RsElement
) : RsFileBase(fileViewProvider), PsiCodeFragment {

    override val containingMod: RsMod
        get() = context.containingMod

    override val crateRoot: RsMod?
        get() = context.crateRoot

    override fun accept(visitor: PsiElementVisitor) {
        visitor.visitFile(this)
    }

    override fun getFileType(): FileType = RsFileType

    private var viewProvider = super.getViewProvider() as SingleRootFileViewProvider
    private var forcedResolveScope: GlobalSearchScope? = null
    private var isPhysical = true

    init {
        getViewProvider().forceCachedPsi(this)
        init(TokenType.CODE_FRAGMENT, contentElementType)
    }

    final override fun init(elementType: IElementType, contentElementType: IElementType?) {
        super.init(elementType, contentElementType)
    }

    override fun isPhysical() = isPhysical

    override fun forceResolveScope(scope: GlobalSearchScope?) {
        forcedResolveScope = scope
    }

    override fun getForcedResolveScope(): GlobalSearchScope? = forcedResolveScope

    override fun getContext(): PsiElement? = context

    final override fun getViewProvider(): SingleRootFileViewProvider = viewProvider

    override fun isValid() = true

    override fun clone(): PsiFileImpl {
        val clone = cloneImpl(calcTreeElement().clone() as FileElement) as RsCodeFragment
        clone.isPhysical = false
        clone.myOriginalFile = this
        clone.viewProvider =
            SingleRootFileViewProvider(PsiManager.getInstance(project), LightVirtualFile(name, RsLanguage, text), false)
        clone.viewProvider.forceCachedPsi(clone)
        return clone
    }

    companion object {
        @JvmStatic
        protected fun createFileViewProvider(
            project: Project,
            text: CharSequence,
            eventSystemEnabled: Boolean
        ): FileViewProvider {
            return PsiManagerEx.getInstanceEx(project).fileManager.createFileViewProvider(
                LightVirtualFile("fragment.rs", RsLanguage, text),
                eventSystemEnabled
            )
        }
    }
}

class RsExpressionCodeFragment(
    fileViewProvider: FileViewProvider,
    context: RsElement
) : RsCodeFragment(fileViewProvider, RsElementTypes.EXPR_CODE_FRAGMENT, context), RsInferenceContextOwner {
    constructor(project: Project, text: CharSequence, eventSystemEnabled: Boolean, context: RsElement)
        : this(createFileViewProvider(project, text, eventSystemEnabled), context)

    val expr: RsExpr? get() = PsiTreeUtil.getChildOfType(this, RsExpr::class.java)
}

class RsPathCodeFragment(
    fileViewProvider: FileViewProvider,
    context: RsElement,
    mode: PathParsingMode,
    val ns: Set<Namespace>
) : RsCodeFragment(fileViewProvider, mode.elementType(), context), RsInferenceContextOwner {
    constructor(
        project: Project,
        text: CharSequence,
        eventSystemEnabled: Boolean,
        context: RsElement,
        mode: PathParsingMode,
        ns: Set<Namespace>
    ) : this(createFileViewProvider(project, text, eventSystemEnabled), context, mode, ns)

    val path: RsPath? get() = PsiTreeUtil.getChildOfType(this, RsPath::class.java)

    companion object {
        @JvmStatic
        private fun PathParsingMode.elementType() = when (this) {
            PathParsingMode.NO_COLONS -> RsElementTypes.PATH_WITHOUT_COLONS_CODE_FRAGMENT
            else -> RsElementTypes.PATH_WITH_COLONS_CODE_FRAGMENT
        }
    }
}
