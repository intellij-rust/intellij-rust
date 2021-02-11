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
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.resolve.resolveDollarCrateIdentifier
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
    get() = path.referenceName.orEmpty() // TODO return null if `referenceName` is null

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
            stub.bodyTextRange
        } else {
            macroArgumentElement?.textRange?.let { TextRange(it.startOffset + 1, it.endOffset - if (it.length == 1) 0 else 1) }
        }
    }

val RsMacroCallStub.bodyTextRange: TextRange?
    get() {
        val bodyStartOffset = bodyStartOffset
        val macroBody = macroBody
        return if (bodyStartOffset != -1 && macroBody !== null) {
            TextRange(bodyStartOffset, bodyStartOffset + macroBody.length)
        } else {
            null
        }
    }

private val MACRO_ARGUMENT_TYPES: TokenSet = tokenSetOf(
    MACRO_ARGUMENT, FORMAT_MACRO_ARGUMENT, LOG_MACRO_ARGUMENT,
    ASSERT_MACRO_ARGUMENT, EXPR_MACRO_ARGUMENT, VEC_MACRO_ARGUMENT,
    CONCAT_MACRO_ARGUMENT, ENV_MACRO_ARGUMENT, ASM_MACRO_ARGUMENT
)

val RsMacroCall.macroArgumentElement: RsElement?
    get() = node.findChildByType(MACRO_ARGUMENT_TYPES)?.psi as? RsElement

val RsExpr.value: String? get() = getValue(null)

/** [crateOrNull] is passed to avoid trigger resolve */
private fun RsExpr.getValue(crateOrNull: Crate?): String? {
    return when (this) {
        is RsLitExpr -> stringValue
        is RsMacroExpr -> {
            val macroCall = macroCall
            when (macroCall.macroName) {
                "concat" -> {
                    val exprList = macroCall.concatMacroArgument?.exprList ?: return null
                    buildString {
                        for (expr in exprList) {
                            val value = expr.getValue(crateOrNull) ?: return null
                            append(value)
                        }
                    }
                }
                "env" -> {
                    val expr = macroCall.envMacroArgument?.variableNameExpr as? RsLitExpr ?: return null
                    val crate = crateOrNull ?: expr.containingCrate ?: return null
                    when (val variableName = expr.getValue(crate)) {
                        "OUT_DIR" -> crate.outDir?.path
                        else -> crate.env[variableName]
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
    val file = (findMacroCallExpandedFrom() ?: this).containingFile?.originalFile?.virtualFile ?: return null
    return file.parent?.findFileByMaybeRelativePath(path)?.toPsiFile(project)?.rustFile
}

/** [crate] is passed to avoid trigger resolve */
fun RsMacroCallStub.getIncludeMacroArgument(crate: Crate): String? {
    val includeMacroArgument = findChildStubByType(RsStubElementTypes.INCLUDE_MACRO_ARGUMENT) ?: return null
    // TODO: Don't use psi
    return includeMacroArgument.psi.expr?.getValue(crate)
}

val RsMacroCall.bodyHash: HashCode?
    get() {
        val stub = greenStub
        if (stub !== null) return stub.bodyHash
        return CachedValuesManager.getCachedValue(this) {
            val body = macroBody
            val hash = body?.let { HashCode.compute(it) }
            CachedValueProvider.Result.create(hash, modificationTracker)
        }
    }

fun RsMacroCall.resolveToMacro(): RsMacro? =
    path.reference?.resolve() as? RsMacro

val RsMacroCall.expansion: MacroExpansion?
    get() {
        val mgr = project.macroExpansionManager
        if (mgr.expansionState != null) return mgr.getExpansionFor(this).value

        return CachedValuesManager.getCachedValue(this) {
            val originalOrSelf = CompletionUtil.getOriginalElement(this)?.takeIf {
                // Use the original element only if macro bodies are equal. They
                // will be different if completion invoked inside the macro body.
                it.macroBody == this.macroBody
            } ?: this
            mgr.getExpansionFor(originalOrSelf)
        }
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

fun RsMacroCall.expandMacrosRecursively(
    depthLimit: Int = DEFAULT_RECURSION_LIMIT,
    replaceDollarCrate: Boolean = true,
    expander: (RsMacroCall) -> MacroExpansion? = RsMacroCall::expansion
): String {
    if (depthLimit == 0) return text

    fun toExpandedText(element: PsiElement): String =
        when (element) {
            is RsMacroCall -> element.expandMacrosRecursively(depthLimit - 1, replaceDollarCrate)
            is RsElement -> if (replaceDollarCrate && element is RsPath && element.referenceName == MACRO_DOLLAR_CRATE_IDENTIFIER
                && element.qualifier == null && element.typeQual == null && !element.hasColonColon) {
                // Replace `$crate` to a crate name. Note that the name can be incorrect because of crate renames
                // and the fact that `$crate` can come from a transitive dependency
                "::" + (element.resolveDollarCrateIdentifier()?.normName ?: element.referenceName.orEmpty())
            } else {
                element.childrenWithLeaves.joinToString(" ") { toExpandedText(it) }
            }
            else -> element.text
        }

    return expander(this)?.elements?.joinToString(" ") { toExpandedText(it) } ?: text
}

fun RsMacroCall.processExpansionRecursively(processor: (RsExpandedElement) -> Boolean): Boolean =
    processExpansionRecursively(processor, 0)

private fun RsMacroCall.processExpansionRecursively(processor: (RsExpandedElement) -> Boolean, depth: Int): Boolean {
    if (depth > DEFAULT_RECURSION_LIMIT) return true
    return expansion?.elements.orEmpty().any { it.processRecursively(processor, depth) }
}

private fun RsExpandedElement.processRecursively(processor: (RsExpandedElement) -> Boolean, depth: Int): Boolean {
    return when (this) {
        is RsMacroCall -> isEnabledByCfgSelf && processExpansionRecursively(processor, depth + 1)
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
