/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.io.IOUtil
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.macros.proc.ProcMacroApplicationService
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsPossibleMacroCallKind.MacroCall
import org.rust.lang.core.psi.ext.RsPossibleMacroCallKind.MetaItem
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.resolve.KnownDerivableTrait
import org.rust.lang.core.resolve.resolveDollarCrateIdentifier
import org.rust.lang.core.resolve2.resolveToMacroWithoutPsi
import org.rust.lang.core.resolve2.resolveToProcMacroWithoutPsi
import org.rust.lang.core.stubs.RsAttrProcMacroOwnerStub
import org.rust.lang.core.stubs.RsAttributeOwnerStub
import org.rust.lang.utils.evaluation.CfgEvaluator
import org.rust.openapiext.testAssert
import org.rust.stdext.HashCode
import org.rust.stdext.exhaustive

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

val PsiElement.ancestorMacroCall: RsPossibleMacroCall?
    get() = ancestors
        .filterIsInstance<RsPossibleMacroCall>()
        .find { it.isMacroCall }

/**
 * Returns `true` if the element is a macro call.
 * It can trigger name resolution. Use [RsPossibleMacroCall.canBeMacroCall] for syntax-based check.
 */
val RsPossibleMacroCall.isMacroCall: Boolean
    get() = when (val kind = kind) {
        is MacroCall -> true
        is MetaItem -> RsProcMacroPsiUtil.canBeProcMacroCall(kind.meta) && kind.meta.resolveToProcMacroWithoutPsi() != null
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

val RsPossibleMacroCall.shouldSkipMacroExpansion: Boolean
    get() = when (val kind = kind) {
        is MetaItem -> !ProcMacroApplicationService.isEnabled()
            || RsProcMacroPsiUtil.canBeCustomDerive(kind.meta) && KnownDerivableTrait.shouldUseHardcodedTraitDerive(kind.meta.name)
        else -> false
    }

val RsPossibleMacroCall.isTopLevelExpansion: Boolean
    get() = when (val kind = kind) {
        is MacroCall -> kind.call.isTopLevelExpansion
        is MetaItem -> kind.meta.canBeMacroCall
        else -> error("unreachable")
    }

val RsPossibleMacroCall.macroBody: MacroCallBody?
    get() = when (val kind = kind) {
        is MacroCall -> kind.call.macroBody?.let { MacroCallBody.FunctionLike(it) }
        is MetaItem -> {
            val owner = kind.meta.owner
            when (val body = owner?.preparedProcMacroCallBody) {
                is PreparedProcMacroCallBody.Attribute -> body.body.takeIf { body.attr.attr == this }
                is PreparedProcMacroCallBody.Derive -> body.body.takeIf {
                    owner is RsStructOrEnumItemElement && RsProcMacroPsiUtil.canBeCustomDerive(kind.meta)
                }
                null -> null
            }
        }
    }

private val RsDocAndAttributeOwner.preparedProcMacroCallBody: PreparedProcMacroCallBody?
    get() = CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result(
            doPrepareProcMacroCallBody(this),
            PsiModificationTracker.MODIFICATION_COUNT
        )
    }

/**
 * TODO comment
 */
private fun doPrepareProcMacroCallBody(
    owner: RsDocAndAttributeOwner,
    explicitCrate: Crate? = null,
    stub: RsAttributeOwnerStub? = owner.attributeStub,
    project: Project = owner.project,
): PreparedProcMacroCallBody? {
    val text = owner.stubbedText ?: return null
    val endOfAttrsOffset = owner.endOfAttrsOffset

    return when (val attr = ProcMacroAttribute.getProcMacroAttribute(owner, stub, explicitCrate)) {
        is ProcMacroAttribute.Attr -> {
            val attrIndex = attr.index
            val crate = explicitCrate ?: owner.containingCrate ?: return null
            val body = doPrepareAttributeProcMacroCallBody(project, text, endOfAttrsOffset, crate, attrIndex) ?: return null
            PreparedProcMacroCallBody.Attribute(body, attr)
        }
        ProcMacroAttribute.Derive -> {
            val crate = explicitCrate ?: owner.containingCrate ?: return null
            val body = doPrepareCustomDeriveMacroCallBody(project, text, endOfAttrsOffset, crate) ?: return null
            PreparedProcMacroCallBody.Derive(body)
        }
        ProcMacroAttribute.None -> null
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
 * Does things that Rustc does before passing a body to a custom derive proc macro:
 * removes `cfg` and `derive` attributes, unwraps `cfg_attr` attributes, moves docs before other attributes.
 */
private fun doPrepareCustomDeriveMacroCallBody(
    project: Project,
    text: String,
    endOfAttrsOffset: Int,
    crate: Crate
): MacroCallBody.Derive? {
    if (endOfAttrsOffset == 0) return null // Impossible? There must be at least one `derive` attribute
    val item = RsPsiFactory(project, markGenerated = false)
        .createFile(text.substring(0, endOfAttrsOffset) + "struct S;")
        .firstChild as? RsStructOrEnumItemElement
        ?: return null
    val evaluator = CfgEvaluator.forCrate(crate)
    val docs = mutableListOf<PsiElement>()
    val attrs = mutableListOf<RsMetaItem>()
    for (child in item.childrenWithLeaves) {
        when (child) {
            is RsDocCommentImpl -> docs += child
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
    return MacroCallBody.Derive(sb.toString())
}

/**
 * Does things that Rustc does before passing a body to an attribute proc macro:
 * removes `cfg` attributes and the proc macro call attribute, unwraps `cfg_attr` attributes,
 * moves non built-in attributes to the end
 */
fun doPrepareAttributeProcMacroCallBody(
    project: Project,
    text: String,
    endOfAttrsOffset: Int,
    crate: Crate,
    attrIndex: Int
): MacroCallBody.Attribute? {
    if (endOfAttrsOffset == 0) return null // Impossible? There must be at least one `derive` attribute
    val item = RsPsiFactory(project, markGenerated = false)
        .createFile(text.substring(0, endOfAttrsOffset) + "struct S;")
        .firstChild as? RsStructOrEnumItemElement
        ?: return null
    val evaluator = CfgEvaluator.forCrate(crate)
    var theMacroCallAttr: RsMetaItem? = null
//    val docs = mutableListOf<PsiElement>()
    val attrs = mutableListOf<PsiElement>()
    val attrsLast = mutableListOf<RsMetaItem>()
    var attrCounter = 0
    for (child in item.childrenWithLeaves) {
        when (child) {
            is RsDocCommentImpl -> attrs += child
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
        b.metaItemArgs?.let { MappedText.single(it.text, it.textOffset) } ?: MappedText.EMPTY
    )
}

private fun MutableMappedText.appendAttrOrDocComment(attrOrDoc: PsiElement) {
    if (attrOrDoc is RsDocCommentImpl) {
        appendMapped(attrOrDoc)
        appendUnmapped("\n")
    } else {
        appendUnmapped("#[")
        appendMapped(attrOrDoc)
        appendUnmapped("]\n")
    }
}

private fun MutableMappedText.appendAttr(meta: RsMetaItem) {
    appendUnmapped("#[")
    appendMapped(meta)
    appendUnmapped("]\n")
}

private fun MutableMappedText.appendMapped(psi: PsiElement) {
    appendMapped(psi.text, psi.startOffset)
}

val RsDocAndAttributeOwner.stubbedText: String?
    get() {
        val stub = (this as StubBasedPsiElementBase<*>).greenStub as? RsAttrProcMacroOwnerStub
        if (stub != null) {
            return stub.stubbedText
        }

        return text
    }

val RsDocAndAttributeOwner.endOfAttrsOffset: Int
    get() {
        val stub = (this as StubBasedPsiElementBase<*>).greenStub as? RsAttrProcMacroOwnerStub
        if (stub != null) {
            return stub.endOfAttrsOffset
        }

        val firstKeyword = firstKeyword ?: return 0
        return firstKeyword.startOffsetInParent
    }

val RsDocAndAttributeOwner.firstKeyword: PsiElement?
    get() = childrenWithLeaves.firstOrNull { it !is RsAttr && it.elementType !in RS_COMMENTS && it !is PsiWhiteSpace }

val RsPossibleMacroCall.bodyTextRange: TextRange?
    get() = when (this) {
        is RsMacroCall -> bodyTextRange
        is RsMetaItem -> owner?.bodyTextRange
        else -> null
    }

val RsDocAndAttributeOwner.bodyTextRange: TextRange?
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
    get() = owner?.bodyHash

private val RsDocAndAttributeOwner.bodyHash: HashCode?
    get() {
        val stub = (this as StubBasedPsiElementBase<*>).greenStub as? RsAttrProcMacroOwnerStub
        if (stub != null) {
            // If `stub.bodyHash` is `null`, there are `cfg_attr` attributes
            return stub.bodyHash ?: preparedProcMacroCallBody?.body?.bodyHash
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
        is MacroCallBody.Derive -> HashCode.compute(item)
        is MacroCallBody.FunctionLike -> error("unreachable")
    }

fun RsPossibleMacroCall.resolveToMacroWithoutPsi(): RsMacroDataWithHash<*>? = when (val kind = kind) {
    is MacroCall -> kind.call.resolveToMacroWithoutPsi()
    is MetaItem -> kind.meta.resolveToProcMacroWithoutPsi()
        ?.takeIf { it.procMacroKind == RsProcMacroKind.fromMacroCall(kind.meta) }
        ?.let { RsMacroDataWithHash.fromDefInfo(it) }
}

val RsPossibleMacroCall.expansion: MacroExpansion?
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

fun RsPossibleMacroCall.expandMacrosRecursively(
    depthLimit: Int = DEFAULT_RECURSION_LIMIT,
    replaceDollarCrate: Boolean = true,
    expander: (RsPossibleMacroCall) -> MacroExpansion? = RsPossibleMacroCall::expansion
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
