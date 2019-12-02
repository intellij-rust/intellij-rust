/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.rust.lang.core.macros.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.stubs.RsMacroCallStub
import org.rust.openapiext.findFileByMaybeRelativePath
import org.rust.openapiext.toPsiFile
import org.rust.stdext.HashCode

abstract class RsMacroCallImplMixin : RsStubbedElementImpl<RsMacroCallStub>,
                                      RsMacroCall {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsMacroCallStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)

    override val modificationTracker: SimpleModificationTracker =
        SimpleModificationTracker()

    override fun incModificationCount(element: PsiElement): Boolean {
        modificationTracker.incModificationCount()
        val isStructureModification = ancestors.any { it is RsMacroCall && it.macroName == "include" }
        return !isStructureModification // Note: RsMacroCall is a special case for RsPsiManagerImpl
    }
}

val RsMacroCall.macroName: String
    get() = path.referenceName

val RsMacroCall.bracesKind: MacroBraces?
    get() = macroArgumentElement?.firstChild?.let { MacroBraces.fromToken(it.elementType) }

val RsMacroCall.macroBody: String?
    get() {
        val stub = greenStub
        if (stub != null) return stub.macroBody
        // Note: `node` is usually an instance of `LazyParseableElement` where `chars` is cached
        return macroArgumentElement?.node?.chars?.let { it.subSequence(1, it.length - if (it.length == 1) 0 else 1) }?.toString()
    }

val RsMacroCall.bodyTextRange: TextRange?
    get() {
        val stub = greenStub
        return if (stub != null) {
            val bodyStartOffset = stub.bodyStartOffset
            val macroBody = stub.macroBody
            if (bodyStartOffset != -1 && macroBody != null) {
                TextRange(bodyStartOffset, bodyStartOffset + macroBody.length)
            } else {
                null
            }
        } else {
            macroArgumentElement?.textRange?.let { TextRange(it.startOffset + 1, it.endOffset - if (it.length == 1) 0 else 1) }
        }
    }

private val MACRO_ARGUMENT_TYPES: TokenSet = tokenSetOf(
    MACRO_ARGUMENT, FORMAT_MACRO_ARGUMENT, LOG_MACRO_ARGUMENT,
    ASSERT_MACRO_ARGUMENT, EXPR_MACRO_ARGUMENT, VEC_MACRO_ARGUMENT,
    CONCAT_MACRO_ARGUMENT, ENV_MACRO_ARGUMENT
)

private val RsMacroCall.macroArgumentElement: RsElement?
    get() = node.findChildByType(MACRO_ARGUMENT_TYPES)?.psi as? RsElement

private val RsExpr.value: String? get() {
    return when (this) {
        is RsLitExpr -> stringValue
        is RsMacroExpr -> {
            val macroCall = macroCall
            when (macroCall.macroName) {
                "concat" -> {
                    val exprList = macroCall.concatMacroArgument?.exprList ?: return null
                    buildString {
                        for (expr in exprList) {
                            val value = expr.value ?: return null
                            append(value)
                        }
                    }
                }
                "env" -> {
                    val expr = macroCall.envMacroArgument?.variableNameExpr as? RsLitExpr ?: return null
                    val pkg = expr.containingCargoPackage ?: return null
                    when (val variableName = expr.value) {
                        "OUT_DIR" -> pkg.outDir?.path
                        else -> pkg.env[variableName]
                    }
                }
                else -> null
            }
        }
        else -> null
    }
}

fun RsMacroCall.findIncludingFile(): RsFile? {
    if (macroName != "include") return null
    val path = includeMacroArgument?.expr?.value ?: return null
    // TODO: it doesn't work if `include!()` macro call comes from other macro
    val file = containingFile?.originalFile?.virtualFile ?: return null
    return file.parent?.findFileByMaybeRelativePath(path)?.toPsiFile(project)?.rustFile
}

val RsMacroCall.bodyHash: HashCode?
    get() = CachedValuesManager.getCachedValue(this) {
        val body = macroBody
        val hash = body?.let { HashCode.compute(it) }
        CachedValueProvider.Result.create(hash, modificationTracker)
    }

fun RsMacroCall.resolveToMacro(): RsMacro? =
    path.reference.resolve() as? RsMacro

val RsMacroCall.expansion: MacroExpansion?
    get() = CachedValuesManager.getCachedValue(this) {
        val project = project
        val originalOrSelf = CompletionUtil.getOriginalElement(this)?.takeIf {
            // Use the original element only if macro bodies are equal. They
            // will be different if completion invoked inside the macro body.
            it.macroBody == this.macroBody
        } ?: this
        project.macroExpansionManager.getExpansionFor(originalOrSelf)
    }

val RsMacroCall.expansionFlatten: List<RsExpandedElement>
    get() {
        val list = mutableListOf<RsExpandedElement>()
        processExpansionRecursively {
            list.add(it)
            false
        }
        return list
    }

fun RsMacroCall.expandAllMacrosRecursively(): String =
    expandAllMacrosRecursively(0)

private fun RsMacroCall.expandAllMacrosRecursively(depth: Int): String {
    if (depth > DEFAULT_RECURSION_LIMIT) return text

    fun toExpandedText(element: PsiElement): String =
        when (element) {
            is RsMacroCall -> element.expandAllMacrosRecursively(depth)
            is RsElement -> element.childrenWithLeaves.joinToString(" ") { toExpandedText(it) }
            else -> element.text
        }

    return expansion?.elements?.joinToString(" ") { toExpandedText(it) } ?: text
}

fun RsMacroCall.processExpansionRecursively(processor: (RsExpandedElement) -> Boolean): Boolean =
    processExpansionRecursively(processor, 0)

private fun RsMacroCall.processExpansionRecursively(processor: (RsExpandedElement) -> Boolean, depth: Int): Boolean {
    if (depth > DEFAULT_RECURSION_LIMIT) return true
    return expansion?.elements.orEmpty().any { it.processRecursively(processor, depth) }
}

private fun RsExpandedElement.processRecursively(processor: (RsExpandedElement) -> Boolean, depth: Int): Boolean {
    return when (this) {
        is RsMacroCall -> isEnabledByCfg && processExpansionRecursively(processor, depth + 1)
        else -> processor(this)
    }
}

fun RsMacroCall.replaceWithExpr(expr: RsExpr): RsElement {
    return when (val context = expansionContext) {
        MacroExpansionContext.EXPR -> parent.replace(expr)
        MacroExpansionContext.STMT -> {
            val exprStmt = RsPsiFactory(project).createStatement("();") as RsExprStmt
            exprStmt.expr.replace(expr)
            replace(exprStmt)
        }
        else -> error("`replaceWithExpr` can only be used for expr or stmt context macros; got $context context")
    } as RsElement
}
