/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.io.IOUtil
import org.jetbrains.annotations.VisibleForTesting
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.macros.errors.GetMacroExpansionError
import org.rust.lang.core.macros.errors.ResolveMacroWithoutPsiError
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsPossibleMacroCallKind.MacroCall
import org.rust.lang.core.psi.ext.RsPossibleMacroCallKind.MetaItem
import org.rust.lang.core.resolve.resolveDollarCrateIdentifier
import org.rust.lang.core.resolve2.resolveToMacroWithoutPsi
import org.rust.lang.core.resolve2.resolveToProcMacroWithoutPsi
import org.rust.lang.core.stubs.RsAttrProcMacroOwnerStub
import org.rust.lang.core.stubs.RsAttributeOwnerStub
import org.rust.lang.doc.psi.RsDocComment
import org.rust.lang.utils.evaluation.CfgEvaluator
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.testAssert
import org.rust.stdext.*
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok

/**
 * A PSI element that can be a declarative or (function-like, derive or attribute) procedural macro call.
 * It is implemented by [RsMacroCall] and [RsMetaItem]. A _possible_ macro call is a _real_ macro call if
 * [isMacroCall] returns `true` for it.
 */
interface RsPossibleMacroCall : RsExpandedElement {
    val path: RsPath?
}

sealed class RsPossibleMacroCallKind {
    data class MacroCall(val call: RsMacroCall) : RsPossibleMacroCallKind()
    data class MetaItem(val meta: RsMetaItem) : RsPossibleMacroCallKind()
}

val RsPossibleMacroCall.kind: RsPossibleMacroCallKind
    get() = when (this) {
        is RsMacroCall -> MacroCall(this)
        is RsMetaItem -> MetaItem(this)
        else -> error("unreachable")
    }

val PsiElement.contextMacroCall: RsPossibleMacroCall?
    get() = contexts
        .filterIsInstance<RsPossibleMacroCall>()
        .find { it.isMacroCall }

/**
 * Returns `true` if the element is a macro call.
 * It can trigger name resolution. Use [RsPossibleMacroCall.canBeMacroCall] for syntax-based check.
 */
val RsPossibleMacroCall.isMacroCall: Boolean
    get() {
        return when (val kind = kind) {
            is MacroCall -> true
            is MetaItem -> {
                val owner = kind.meta.owner as? RsAttrProcMacroOwner ?: return false
                when (val attr = ProcMacroAttribute.getProcMacroAttribute(owner, ignoreProcMacrosDisabled = true)) {
                    is ProcMacroAttribute.Attr -> attr.attr == this
                    is ProcMacroAttribute.Derive -> RsProcMacroPsiUtil.canBeCustomDerive(kind.meta)
                    null -> {
                        val attrs = ProcMacroAttribute.getProcMacroAttributeWithoutResolve(owner, ignoreProcMacrosDisabled = true)
                        for (attr1 in attrs) {
                            when (attr1) {
                                is ProcMacroAttribute.Attr -> if (attr1.attr == this) {
                                    return true
                                }
                                is ProcMacroAttribute.Derive -> {
                                    return RsProcMacroPsiUtil.canBeCustomDerive(kind.meta)
                                }
                            }
                        }
                        false
                    }
                }
            }
        }
    }

/**
 * A syntax-based lightweight check. Returns `false` if the element can't be a macro call
 * @see RsPossibleMacroCall.isMacroCall
 */
val RsPossibleMacroCall.canBeMacroCall: Boolean
    get() = when (val kind = kind) {
        is MacroCall -> true
        is MetaItem -> RsProcMacroPsiUtil.canBeProcMacroCall(kind.meta)
    }

val RsPossibleMacroCall.isTopLevelExpansion: Boolean
    get() = when (val kind = kind) {
        is MacroCall -> kind.call.isTopLevelExpansion
        is MetaItem -> kind.meta.canBeMacroCall && kind.meta.owner?.parent is RsMod
    }

val RsPossibleMacroCall.macroBody: MacroCallBody?
    get() = when (val kind = kind) {
        is MacroCall -> kind.call.macroBody?.let { MacroCallBody.FunctionLike(it) }
        is MetaItem -> {
            val owner = kind.meta.owner as? RsAttrProcMacroOwner
            when (val body = owner?.preparedProcMacroCallBody) {
                is PreparedProcMacroCallBody.Attribute -> body.body.takeIf { body.attr.attr == this }
                is PreparedProcMacroCallBody.Derive -> body.body.takeIf {
                    owner is RsStructOrEnumItemElement && RsProcMacroPsiUtil.canBeCustomDerive(kind.meta)
                }
                null -> null
            }
        }
    }

private val RsAttrProcMacroOwner.preparedProcMacroCallBody: PreparedProcMacroCallBody?
    get() = CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result(
            doPrepareProcMacroCallBody(this),
            PsiModificationTracker.MODIFICATION_COUNT
        )
    }

/** See docs for [doPrepareAttributeProcMacroCallBody]/[doPrepareCustomDeriveMacroCallBody] */
private fun doPrepareProcMacroCallBody(
    owner: RsAttrProcMacroOwner,
    explicitCrate: Crate? = null,
    stub: RsAttributeOwnerStub? = owner.attributeStub,
    project: Project = owner.project,
): PreparedProcMacroCallBody? {
    val text = owner.stubbedText ?: return null
    val endOfAttrsOffset = owner.endOfAttrsOffset

    @Suppress("MoveVariableDeclarationIntoWhen")
    val attr = ProcMacroAttribute.getProcMacroAttribute(owner, stub, explicitCrate)

    return when (attr) {
        is ProcMacroAttribute.Attr -> {
            val attrIndex = attr.index
            val crate = explicitCrate ?: owner.containingCrate
            val body = doPrepareAttributeProcMacroCallBody(
                project,
                text,
                endOfAttrsOffset,
                crate,
                attrIndex,
                fixupRustSyntaxErrors = owner is RsFunction
            ) ?: return null
            PreparedProcMacroCallBody.Attribute(body, attr)
        }
        is ProcMacroAttribute.Derive -> {
            val crate = explicitCrate ?: owner.containingCrate
            val body = doPrepareCustomDeriveMacroCallBody(project, text, endOfAttrsOffset, crate) ?: return null
            PreparedProcMacroCallBody.Derive(body)
        }
        null -> null
    }
}

private sealed class PreparedProcMacroCallBody {
    abstract val body: MacroCallBody

    data class Derive(override val body: MacroCallBody.Derive) : PreparedProcMacroCallBody()

    data class Attribute(
        override val body: MacroCallBody.Attribute,
        val attr: ProcMacroAttribute.Attr<RsMetaItem>
    ) : PreparedProcMacroCallBody()
}

/**
 * Does things that Rustc does before passing a body to a **custom derive** proc macro:
 * removes `cfg` and `derive` attributes, unwraps `cfg_attr` attributes, moves docs before other attributes.
 */
fun doPrepareCustomDeriveMacroCallBody(
    project: Project,
    text: String,
    endOfAttrsOffset: Int,
    crate: Crate
): MacroCallBody.Derive? {
    val item = createAttributeHolderPsi(project, text, endOfAttrsOffset) ?: return null
    val evaluator = CfgEvaluator.forCrate(crate)
    val docs = mutableListOf<PsiElement>()
    val attrs = mutableListOf<RsMetaItem>()
    for (child in item.childrenWithLeaves) {
        when (child) {
            is RsDocComment -> docs += child
            is RsOuterAttr -> evaluator.expandCfgAttrs(sequenceOf(child.metaItem)).forEach {
                when (it.name) {
                    "cfg", "derive" -> Unit
                    "doc" -> docs += it
                    else -> attrs += it
                }
            }
            is PsiComment, is PsiWhiteSpace -> continue
            else -> {
                testAssert { child.startOffsetInParent == endOfAttrsOffset }
                break
            }
        }
    }

    val sb = MutableMappedText(text.length)
    for (doc in docs) {
        sb.appendAttrOrDocComment(doc)
    }
    for (meta in attrs) {
        sb.appendAttr(meta)
    }
    sb.appendMapped(text.substring(endOfAttrsOffset, text.length), endOfAttrsOffset)
    return MacroCallBody.Derive(sb.toMappedText())
}

/**
 * Does things that Rustc does before passing a body to an **attribute** proc macro:
 * removes `cfg` attributes and the proc macro call attribute, unwraps `cfg_attr` attributes,
 * moves non built-in attributes to the end
 */
fun doPrepareAttributeProcMacroCallBody(
    project: Project,
    text: String,
    endOfAttrsOffset: Int,
    crate: Crate,
    attrIndex: Int,
    fixupRustSyntaxErrors: Boolean,
): MacroCallBody.Attribute? {
    val item = createAttributeHolderPsi(project, text, endOfAttrsOffset) ?: return null
    val evaluator = CfgEvaluator.forCrate(crate)
    var theMacroCallAttr: RsMetaItem? = null
    val attrs = mutableListOf<PsiElement>()
    val attrsLast = mutableListOf<RsMetaItem>()
    var attrCounter = 0
    for (child in item.childrenWithLeaves) {
        when (child) {
            is RsDocComment -> attrs += child
            is RsOuterAttr -> {
                evaluator.expandCfgAttrs(sequenceOf(child.metaItem)).forEach {
                    if (attrCounter != attrIndex) {
                        val name = it.name
                        when {
                            name == "cfg" -> Unit
                            attrCounter < attrIndex && name !in RS_BUILTIN_ATTRIBUTES -> attrsLast += it
                            else -> attrs += it
                        }.exhaustive
                    } else {
                        theMacroCallAttr = it
                    }
                    attrCounter++
                }
            }
            is PsiComment, is PsiWhiteSpace -> continue
            else -> {
                testAssert { child.startOffsetInParent == endOfAttrsOffset }
                break
            }
        }
    }

    val sb = MutableMappedText(text.length)
    for (attrOrDoc in attrs) {
        sb.appendAttrOrDocComment(attrOrDoc)
    }
    for (meta in attrsLast) {
        sb.appendAttr(meta)
    }
    sb.appendMapped(text.substring(endOfAttrsOffset, text.length), endOfAttrsOffset)
    val b = theMacroCallAttr ?: return null
    return MacroCallBody.Attribute(
        sb.toMappedText(),
        b.metaItemArgs?.let { MappedText.single(it.text, it.textOffset) } ?: MappedText.EMPTY,
        fixupRustSyntaxErrors
    )
}

private fun createAttributeHolderPsi(project: Project, text: String, endOfAttrsOffset: Int): RsDocAndAttributeOwner? {
    if (endOfAttrsOffset == 0) return null // Impossible? There must be at least one attribute
    return RsPsiFactory(project, markGenerated = false)
        .createFile(text.substring(0, endOfAttrsOffset) + "struct S;")
        .firstChild as? RsDocAndAttributeOwner
}

private fun MutableMappedText.appendAttrOrDocComment(attrOrDoc: PsiElement) {
    when (attrOrDoc) {
        is RsDocComment -> {
            appendMapped(attrOrDoc)
            appendUnmapped("\n")
        }
        is RsMetaItem -> {
            appendAttr(attrOrDoc)
        }
        else -> {
            error("Unsupported element: $attrOrDoc")
        }
    }
}

private fun MutableMappedText.appendAttr(meta: RsMetaItem) {
    val parent = meta.parent
    if (parent is RsOuterAttr) {
        appendMapped(parent)
    } else {
        appendUnmapped("#[")
        appendMapped(meta)
        appendUnmapped("]\n")
    }
}

private fun MutableMappedText.appendMapped(psi: PsiElement) {
    appendMapped(psi.text, psi.startOffset)
}

val RsAttrProcMacroOwner.stubbedText: String?
    get() {
        val stub = (this as StubBasedPsiElementBase<*>).greenStub as? RsAttrProcMacroOwnerStub
        if (stub != null) {
            return stub.stubbedText
        }

        return text
    }

val RsAttrProcMacroOwner.endOfAttrsOffset: Int
    get() {
        val stub = (this as StubBasedPsiElementBase<*>).greenStub as? RsAttrProcMacroOwnerStub
        if (stub != null) {
            return stub.endOfAttrsOffset
        }

        val firstKeyword = childrenWithLeaves.firstOrNull {
            it !is RsAttr && it.elementType !in RS_COMMENTS && it !is PsiWhiteSpace
        } ?: return 0
        return firstKeyword.startOffsetInParent
    }

val RsPossibleMacroCall.bodyTextRange: TextRange?
    get() = when (this) {
        is RsMacroCall -> bodyTextRange
        is RsMetaItem -> owner?.bodyTextRange
        else -> null
    }

private val RsDocAndAttributeOwner.bodyTextRange: TextRange?
    get() {
        val stub = (this as StubBasedPsiElementBase<*>).greenStub as? RsAttrProcMacroOwnerStub
        return if (stub != null) {
            stub.bodyTextRange
        } else {
            textRange
        }
    }

val RsAttrProcMacroOwnerStub.bodyTextRange: TextRange?
    get() {
        val bodyStartOffset = startOffset
        val macroBody = stubbedText
        return if (bodyStartOffset != -1 && macroBody !== null) {
            TextRange(bodyStartOffset, bodyStartOffset + macroBody.length)
        } else {
            null
        }
    }

val RsPossibleMacroCall.bodyHash: HashCode?
    get() = when (val kind = kind) {
        is MacroCall -> kind.call.bodyHash
        is MetaItem -> kind.meta.bodyHash
    }

private val RsMetaItem.bodyHash: HashCode?
    get() = (owner as? RsAttrProcMacroOwner)?.bodyHash

private val RsAttrProcMacroOwner.bodyHash: HashCode?
    get() {
        val stub = (this as StubBasedPsiElementBase<*>).greenStub as? RsAttrProcMacroOwnerStub
        if (stub != null) {
            // If `stub.stubbedTextHash` is `null`, there are `cfg_attr` attributes
            return stub.stubbedTextHash ?: preparedProcMacroCallBody?.body?.bodyHash
        }
        return if (rawAttributes.hasAttribute("cfg_attr")) {
            // There are `cfg_attr` attributes - the macro body (and hence the hash) depends on cfg configuration
            preparedProcMacroCallBody?.body?.bodyHash
        } else {
            // No cfg_attr attributes - just use a hash of the text
            stubbedText?.let { HashCode.compute(it) }
        }
    }

val MacroCallBody.bodyHash: HashCode
    get() = when (this) {
        is MacroCallBody.Attribute -> HashCode.compute {
            IOUtil.writeString(item.text, this)
            item.ranges.writeTo(this)
            IOUtil.writeString(attr.text, this)
            attr.ranges.writeTo(this)
        }
        is MacroCallBody.Derive -> HashCode.compute {
            IOUtil.writeString(item.text, this)
            item.ranges.writeTo(this)
        }
        is MacroCallBody.FunctionLike -> error("unreachable")
    }

fun RsPossibleMacroCall.resolveToMacroWithoutPsi(): RsMacroDataWithHash<*>? = resolveToMacroWithoutPsiWithErr().ok()

fun RsPossibleMacroCall.resolveToMacroWithoutPsiWithErr(
    errorIfIdentity: Boolean = false,
): RsResult<RsMacroDataWithHash<*>, ResolveMacroWithoutPsiError> = when (val kind = kind) {
    is MacroCall -> kind.call.resolveToMacroWithoutPsi()
    is MetaItem -> kind.meta.resolveToProcMacroWithoutPsi().toResult().mapErr { ResolveMacroWithoutPsiError.Unresolved }
        .andThen {
            val callKind = RsProcMacroKind.fromMacroCall(kind.meta)
                ?: return Err(ResolveMacroWithoutPsiError.Unresolved)
            val defKind = it.procMacroKind
            if (defKind == callKind) Ok(it) else Err(ResolveMacroWithoutPsiError.UnmatchedProcMacroKind(callKind, defKind))
        }
        .andThen { RsMacroDataWithHash.fromDefInfo(it, errorIfIdentity) }
}

val RsPossibleMacroCall.expansion: MacroExpansion?
    get() = expansionResult.ok()

val RsPossibleMacroCall.expansionResult: RsResult<MacroExpansion, GetMacroExpansionError>
    get() {
        return CachedValuesManager.getCachedValue(this, RS_MACRO_CALL_EXPANSION_RESULT) {
            val originalOrSelf = CompletionUtil.getOriginalElement(this)?.takeIf {
                // Use the original element only if macro bodies are equal. They
                // will be different if completion invoked inside the macro body.
                it.macroBody == this.macroBody
            } ?: this
            val result = project.macroExpansionManager.getExpansionFor(originalOrSelf)
            checkExpansionResult(originalOrSelf, result.value)
            result
        }
    }

private fun checkExpansionResult(call: RsPossibleMacroCall, result: RsResult<MacroExpansion, GetMacroExpansionError>) {
    if (!isUnitTestMode) return
    if (result !is Ok) return
    if (call.project.macroExpansionManager.macroExpansionMode !is MacroExpansionMode.New) return
    val expandedElement = result.ok.elements.firstOrNull() ?: return

    val expandedFrom = expandedElement.expandedOrIncludedFrom
    check(expandedFrom == call) {
        "macro.expansion.expandedFrom != macro; macro: `$call`, expandedFrom: `$expandedFrom`"
    }

    val expandedElementContext = expandedElement.context
    val macroCallContext = call.contextToSetForExpansion
    check(expandedElementContext == macroCallContext) {
        "macro.expansion.context != macro.context; macro: `$call`, expandedElementContext: `$expandedElementContext`"
    }
}

/**
 * Equivalent to `this.context` in the case of [RsMacroCall], but `this.owner?.context` in the case of
 * [RsMetaItem]. Use as a [RsExpandedElement.getContext] for elements expanded from this macro
 */
val RsPossibleMacroCall.contextToSetForExpansion: PsiElement?
    get() = when (val kind = kind) {
        is MacroCall -> kind.call.context
        is MetaItem -> kind.meta.owner?.context
    }

@VisibleForTesting
val RS_MACRO_CALL_EXPANSION_RESULT: Key<CachedValue<RsResult<MacroExpansion, GetMacroExpansionError>>> =
    Key("org.rust.lang.core.psi.ext.RS_MACRO_CALL_EXPANSION_RESULT")

/**
 * @return `null` only if [sizeLimit] is exceeded
 */
fun RsPossibleMacroCall.expandMacrosRecursively(
    depthLimit: Int = Int.MAX_VALUE,
    replaceDollarCrate: Boolean = true,
    sizeLimit: Int = Int.MAX_VALUE,
    expander: (RsPossibleMacroCall) -> MacroExpansion? = RsPossibleMacroCall::expansion
): String? {
    val builder = StringBuilder()
    val status = expandMacrosRecursively(depthLimit, replaceDollarCrate, sizeLimit, builder, expander)
    return if (status == ExpansionStatus.SizeLimitExceeded) null else builder.toString()
}

private fun RsPossibleMacroCall.expandMacrosRecursively(
    depthLimit: Int,
    replaceDollarCrate: Boolean,
    sizeLimit: Int,
    builder: StringBuilder,
    expander: (RsPossibleMacroCall) -> MacroExpansion? = RsPossibleMacroCall::expansion
): ExpansionStatus {
    if (depthLimit == 0) {
        builder.append(textIfNotExpanded())
        return ExpansionStatus.NotExpanded
    }

    // true means sizeLimit not exceeded
    fun toExpandedText(element: PsiElement): Boolean =
        when (element) {
            is RsMacroCall -> {
                val status = element.expandMacrosRecursively(
                    depthLimit - 1,
                    replaceDollarCrate,
                    sizeLimit,
                    builder,
                )
                status != ExpansionStatus.SizeLimitExceeded
            }
            is RsElement -> if (replaceDollarCrate && element is RsPath && element.referenceName == MACRO_DOLLAR_CRATE_IDENTIFIER
                && element.qualifier == null && element.typeQual == null && !element.hasColonColon) {
                // Replace `$crate` to a crate name. Note that the name can be incorrect because of crate renames
                // and the fact that `$crate` can come from a transitive dependency
                builder.appendWithSizeCheck("::", sizeLimit)
                    && builder.appendWithSizeCheck(
                    element.resolveDollarCrateIdentifier()?.normName ?: element.referenceName.orEmpty(),
                        sizeLimit,
                    )
            } else {
                val attrMacro = (element as? RsAttrProcMacroOwner)?.procMacroAttribute?.attr
                if (attrMacro != null) {
                    val status = attrMacro.expandMacrosRecursively(depthLimit - 1, replaceDollarCrate, sizeLimit, builder)
                    status != ExpansionStatus.SizeLimitExceeded
                } else {
                    element
                        .childrenWithLeaves
                        .iterator()
                        .joinToStringWithSizeCheck(builder, " ", sizeLimit) {
                            toExpandedText(it)
                        }
                }
            }
            else -> builder.appendWithSizeCheck(element.text, sizeLimit)
        }

    val elements = expander(this)?.elements?.iterator()
    if (elements == null) {
        builder.append(textIfNotExpanded())
        return ExpansionStatus.NotExpanded
    }
    return if (elements.joinToStringWithSizeCheck(builder, " ", sizeLimit) { toExpandedText(it) }) {
        ExpansionStatus.Expanded
    } else {
        ExpansionStatus.SizeLimitExceeded
    }
}

private enum class ExpansionStatus {
    Expanded, NotExpanded, SizeLimitExceeded
}

private inline fun <T> Iterator<T>.joinToStringWithSizeCheck(
    builder: StringBuilder,
    separator: String,
    sizeLimit: Int,
    transformWithSizeCheck: (T) -> Boolean // should put in [builder] with size check of [sizeLimit]
): Boolean {
    var success = true
    while (success && hasNext()) {
        val next = next()
        success = if (transformWithSizeCheck(next)) {
            if (this.hasNext()) {
                builder.appendWithSizeCheck(separator, sizeLimit)
            } else {
                true
            }
        } else {
            false
        }
    }
    return success
}

private fun StringBuilder.appendWithSizeCheck(str: String, limit: Int): Boolean {
    return if (this.length + str.length <= limit) {
        append(str)
        true
    } else {
        false
    }
}

private fun RsPossibleMacroCall.textIfNotExpanded(): String = when (val kind = kind) {
    is MacroCall -> text
    is MetaItem -> kind.meta.owner?.text ?: ""
}
