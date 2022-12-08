/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.MacroExpansionContext
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.macros.expansionContext
import org.rust.lang.core.macros.findMacroCallExpandedFrom
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.stubs.RsMacroCallStub
import org.rust.openapiext.findFileByMaybeRelativePath
import org.rust.openapiext.isUnitTestMode
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

val RsMacroCall.isTopLevelExpansion: Boolean
    get() = parent is RsMod

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
    MACRO_ARGUMENT, FORMAT_MACRO_ARGUMENT,
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
                    val crate = crateOrNull ?: expr.containingCrate
                    when (val variableName = expr.getValue(crate)) {
                        "OUT_DIR" -> crate.outDir?.path
                        else -> {
                            val toolchain = if (isUnitTestMode) RsToolchainBase.suggest() else crate.project.toolchain
                            crate.env[variableName]?.let { toolchain?.toLocalPath(it) }
                        }
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

fun RsMacroCall.resolveToMacro(): RsMacroDefinitionBase? =
    path.reference?.resolve() as? RsMacroDefinitionBase

val RsMacroCall.expansionFlatten: List<RsExpandedElement>
    get() {
        val list = mutableListOf<RsExpandedElement>()
        processExpansionRecursively {
            list.add(it)
            false
        }
        return list
    }

fun RsMacroCall.processExpansionRecursively(processor: (RsExpandedElement) -> Boolean): Boolean {
    return expansion?.elements.orEmpty().any { it.processRecursively(processor) }
}

private fun RsExpandedElement.processRecursively(processor: (RsExpandedElement) -> Boolean): Boolean {
    return when (this) {
        is RsMacroCall -> existsAfterExpansionSelf && processExpansionRecursively(processor)
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

val RsMacroCall.isStdTryMacro
    get() = macroName == "try" && resolveToMacro()?.containingCargoPackage?.origin == PackageOrigin.STDLIB
