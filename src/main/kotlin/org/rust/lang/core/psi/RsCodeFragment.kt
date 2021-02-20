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
import com.intellij.testFramework.LightVirtualFile
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.parser.RustParserUtil.PathParsingMode
import org.rust.lang.core.parser.RustParserUtil.PathParsingMode.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.Namespace

abstract class RsCodeFragment(
    fileViewProvider: FileViewProvider,
    contentElementType: IElementType,
    open val context: RsElement,
    forceCachedPsi: Boolean = true,
    val importTarget: RsItemsOwner? = null,
) : RsFileBase(fileViewProvider), PsiCodeFragment, RsItemsOwner {

    constructor(
        project: Project,
        text: CharSequence,
        contentElementType: IElementType,
        context: RsElement,
        importTarget: RsItemsOwner? = null,
    ) : this(
        PsiManagerEx.getInstanceEx(project).fileManager.createFileViewProvider(
            LightVirtualFile("fragment.rs", RsLanguage, text), true
        ),
        contentElementType,
        context,
        importTarget = importTarget
    )

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
        if (forceCachedPsi) {
            getViewProvider().forceCachedPsi(this)
        }
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

    override fun getContext(): PsiElement = context

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

class RsExpressionCodeFragment : RsCodeFragment, RsInferenceContextOwner {
    constructor(fileViewProvider: FileViewProvider, context: RsElement)
        : super(fileViewProvider, RsCodeFragmentElementType.EXPR, context)

    constructor(project: Project, text: CharSequence, context: RsElement, importTarget: RsItemsOwner? = null)
        : super(project, text, RsCodeFragmentElementType.EXPR, context, importTarget = importTarget)

    val expr: RsExpr? get() = childOfType()
}

class RsStatementCodeFragment(project: Project, text: CharSequence, context: RsElement)
    : RsCodeFragment(project, text, RsCodeFragmentElementType.STMT, context) {
    val stmt: RsStmt? get() = childOfType()
}

class RsTypeReferenceCodeFragment(
    project: Project,
    text: CharSequence,
    context: RsElement,
    importTarget: RsItemsOwner? = null,
)
    : RsCodeFragment(project, text, RsCodeFragmentElementType.TYPE_REF, context, importTarget = importTarget),
      RsNamedElement {
    val typeReference: RsTypeReference? get() = childOfType()
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

    val path: RsPath? get() = childOfType()

    companion object {
        @JvmStatic
        private fun PathParsingMode.elementType() = when (this) {
            TYPE -> RsCodeFragmentElementType.TYPE_PATH
            VALUE -> RsCodeFragmentElementType.VALUE_PATH
            NO_TYPE_ARGS -> error("$NO_TYPE_ARGS mode is not supported; use $TYPE")
        }
    }
}

class RsReplCodeFragment(fileViewProvider: FileViewProvider, override var context: RsElement)
    : RsCodeFragment(fileViewProvider, RsCodeFragmentElementType.REPL, context, false),
      RsInferenceContextOwner, RsItemsOwner {

    val expandedStmtsAndTailExpr: Pair<List<RsExpandedElement>, RsExpr?>
        get() {
            val expandedElements = childrenOfType<RsExpandedElement>()
            val tailExpr = expandedElements.lastOrNull()?.let { it as? RsExpr }
            val stmts = when (tailExpr) {
                null -> expandedElements
                else -> expandedElements.subList(0, expandedElements.size - 1)
            }
            return stmts to tailExpr
        }

    // if multiple elements have same name, then we keep only last among them
    val namedElementsUnique: Map<String, RsNamedElement>
        get() = expandedStmtsAndTailExpr.first
            .filterIsInstance<RsNamedElement>()
            .filter { it.name != null }
            .associateBy { it.name!! }
}
