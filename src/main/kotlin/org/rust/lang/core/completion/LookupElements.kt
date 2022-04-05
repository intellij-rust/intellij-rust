/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RsIcons
import org.rust.ide.presentation.getStubOnlyText
import org.rust.ide.refactoring.RsNamesValidator
import org.rust.lang.core.completion.RsLookupElementProperties.KeywordKind
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.FieldResolveVariant
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.infer.ExpectedType
import org.rust.lang.core.types.infer.RsInferenceContext
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.openapiext.Testmark
import org.rust.stdext.exhaustive
import org.rust.stdext.mapToSet

const val KEYWORD_PRIORITY = 80.0
const val PRIMITIVE_TYPE_PRIORITY = KEYWORD_PRIORITY
const val FRAGMENT_SPECIFIER_PRIORITY = KEYWORD_PRIORITY

const val VARIABLE_PRIORITY = 5.0
const val ENUM_VARIANT_PRIORITY = 4.0
const val FIELD_DECL_PRIORITY = 3.0
const val ASSOC_FN_PRIORITY = 2.0
const val DEFAULT_PRIORITY = 0.0
const val MACRO_PRIORITY = -0.1
const val DEPRECATED_PRIORITY = -1.0

private const val EXPECTED_TYPE_PRIORITY_OFFSET = 40.0
private const val MUT_METHOD_PRIORITY_OFFSET = -50.0
private const val LOCAL_PRIORITY_OFFSET = 20.0
private const val INHERENT_IMPL_MEMBER_PRIORITY_OFFSET = 0.1

interface CompletionEntity {
    val ty: Ty?
    fun getBaseLookupElementProperties(context: RsCompletionContext): RsLookupElementProperties
    fun createBaseLookupElement(context: RsCompletionContext): LookupElementBuilder
}

class ScopedBaseCompletionEntity(private val scopeEntry: ScopeEntry) : CompletionEntity {

    private val element = checkNotNull(scopeEntry.element) { "Invalid scope entry" }

    override val ty: Ty? get() = element.asTy

    override fun getBaseLookupElementProperties(context: RsCompletionContext): RsLookupElementProperties {
        val isMethodSelfTypeIncompatible = element is RsFunction && element.isMethod
            && isMutableMethodOnConstReference(element, context.context)

        // It's visible and can't be exported = it's local
        val isLocal = context.isSimplePath && !element.canBeExported

        val elementKind = when {
            element is RsDocAndAttributeOwner && element.queryAttributes.deprecatedAttribute != null -> {
                RsLookupElementProperties.ElementKind.DEPRECATED
            }
            element is RsMacro -> RsLookupElementProperties.ElementKind.MACRO
            element is RsPatBinding -> RsLookupElementProperties.ElementKind.VARIABLE
            element is RsEnumVariant -> RsLookupElementProperties.ElementKind.ENUM_VARIANT
            element is RsFieldDecl -> RsLookupElementProperties.ElementKind.FIELD_DECL
            element is RsFunction && element.isAssocFn -> RsLookupElementProperties.ElementKind.ASSOC_FN
            else -> RsLookupElementProperties.ElementKind.DEFAULT
        }

        val isInherentImplMember = element is RsAbstractable && element.owner.isInherentImpl

        val isOperatorMethod = element is RsFunction
            && scopeEntry is AssocItemScopeEntryBase<*>
            && scopeEntry.source.implementedTrait?.element?.langAttribute in OPERATOR_TRAIT_LANG_ITEMS

        val isBlanketImplMember = if (scopeEntry is AssocItemScopeEntryBase<*>) {
            val source = scopeEntry.source
            source is TraitImplSource.ExplicitImpl && source.type is TyTypeParameter
        } else {
            false
        }

        return RsLookupElementProperties(
            isSelfTypeCompatible = !isMethodSelfTypeIncompatible,
            isLocal = isLocal,
            elementKind = elementKind,
            isInherentImplMember = isInherentImplMember,
            isOperatorMethod = isOperatorMethod,
            isBlanketImplMember = isBlanketImplMember,
            isUnsafeFn = element is RsFunction && element.isActuallyUnsafe,
            isAsyncFn = element is RsFunction && element.isAsync,
            isConstFnOrConst = element is RsFunction && element.isConst || element is RsConstant && element.isConst,
            isExternFn = element is RsFunction && element.isExtern,
        )
    }

    override fun createBaseLookupElement(context: RsCompletionContext): LookupElementBuilder {
        val subst = context.lookup?.ctx?.getSubstitution(scopeEntry) ?: emptySubstitution
        return element.getLookupElementBuilder(scopeEntry.name, subst)
    }

    companion object {
        private val OPERATOR_TRAIT_LANG_ITEMS = OverloadableBinaryOperator.values().mapToSet { it.itemName }
    }
}

private fun isMutableMethodOnConstReference(method: RsFunction, call: RsElement?): Boolean {
    if (call == null) return false
    val self = method.selfParameter ?: return false
    if (!self.isRef || !self.mutability.isMut) return false

    // now we know that the method takes &mut self
    val fieldLookup = call as? RsFieldLookup ?: return false
    val expr = fieldLookup.receiver

    val isMutable = when (val type = expr.type) {
        is TyReference -> type.mutability.isMut
        else -> hasMutBinding(expr)
    }

    return !isMutable
}

private fun hasMutBinding(expr: RsExpr): Boolean {
    val pathExpr = expr as? RsPathExpr ?: return true
    val binding = pathExpr.path.reference?.resolve() as? RsPatBinding ?: return true
    return binding.mutability.isMut
}

fun createLookupElement(
    scopeEntry: ScopeEntry,
    context: RsCompletionContext,
    locationString: String? = null,
    insertHandler: InsertHandler<LookupElement> = RsDefaultInsertHandler()
): LookupElement {
    val completionEntity = ScopedBaseCompletionEntity(scopeEntry)
    return createLookupElement(completionEntity, context, locationString, insertHandler)
}

fun createLookupElement(
    completionEntity: CompletionEntity,
    context: RsCompletionContext,
    locationString: String? = null,
    insertHandler: InsertHandler<LookupElement> = RsDefaultInsertHandler()
): LookupElement {
    val lookup = completionEntity.createBaseLookupElement(context)
        .withInsertHandler(insertHandler)
        .let { if (locationString != null) it.appendTailText(" ($locationString)", true) else it }

    val implLookup = context.lookup
    val isCompatibleTypes = implLookup != null && isCompatibleTypes(implLookup, completionEntity.ty, context.expectedTy)

    val properties = completionEntity.getBaseLookupElementProperties(context)
        .copy(isReturnTypeConformsToExpectedType = isCompatibleTypes)

    return lookup.toRsLookupElement(properties)
}

// BACKCOMPAT: 2021.3 we have to maintain priorities for ML-assisted completion
private fun RsLookupElementProperties.calculateBackCompatPriority(): Double {
    if (isImplMemberFullLineCompletion) {
        return KEYWORD_PRIORITY + 1
    }

    when (keywordKind) {
        KeywordKind.PUB -> return KEYWORD_PRIORITY + 3
        KeywordKind.PUB_CRATE -> return KEYWORD_PRIORITY + 2
        KeywordKind.PUB_PARENS -> return KEYWORD_PRIORITY + 1
        KeywordKind.LAMBDA_EXPR -> return KEYWORD_PRIORITY * 1.01
        KeywordKind.ELSE_BRANCH -> return KEYWORD_PRIORITY * 1.0001
        KeywordKind.AWAIT -> return KEYWORD_PRIORITY * 1.0001
        KeywordKind.KEYWORD -> return KEYWORD_PRIORITY
        KeywordKind.NOT_A_KEYWORD -> Unit
    }.exhaustive

    var priority = when (elementKind) {
        RsLookupElementProperties.ElementKind.DERIVE_GROUP -> 5.1
        RsLookupElementProperties.ElementKind.DERIVE -> 5.0
        RsLookupElementProperties.ElementKind.LINT -> 5.0
        RsLookupElementProperties.ElementKind.LINT_GROUP -> 4.0
        RsLookupElementProperties.ElementKind.VARIABLE -> VARIABLE_PRIORITY
        RsLookupElementProperties.ElementKind.ENUM_VARIANT -> ENUM_VARIANT_PRIORITY
        RsLookupElementProperties.ElementKind.FIELD_DECL -> FIELD_DECL_PRIORITY
        RsLookupElementProperties.ElementKind.ASSOC_FN -> ASSOC_FN_PRIORITY
        RsLookupElementProperties.ElementKind.DEFAULT -> DEFAULT_PRIORITY
        RsLookupElementProperties.ElementKind.MACRO -> MACRO_PRIORITY
        RsLookupElementProperties.ElementKind.DEPRECATED -> DEPRECATED_PRIORITY
    }

    if (!isSelfTypeCompatible) {
        priority += MUT_METHOD_PRIORITY_OFFSET
    }

    if (isLocal) {
        priority += LOCAL_PRIORITY_OFFSET
    }

    if (isInherentImplMember) {
        priority += INHERENT_IMPL_MEMBER_PRIORITY_OFFSET
    }

    if (isReturnTypeConformsToExpectedType) {
        priority += EXPECTED_TYPE_PRIORITY_OFFSET
    }

    return priority
}

private fun RsInferenceContext.getSubstitution(scopeEntry: ScopeEntry): Substitution =
    when (scopeEntry) {
        is AssocItemScopeEntryBase<*> ->
            instantiateMethodOwnerSubstitution(scopeEntry)
                .mapTypeValues { (_, v) -> resolveTypeVarsIfPossible(v) }
                .mapConstValues { (_, v) -> resolveTypeVarsIfPossible(v) }
        is FieldResolveVariant ->
            scopeEntry.selfTy.typeParameterValues
        else ->
            emptySubstitution
    }

private val RsElement.asTy: Ty?
    get() = when (this) {
        is RsConstant -> typeReference?.type
        is RsConstParameter -> typeReference?.type
        is RsFieldDecl -> typeReference?.type
        is RsFunction -> retType?.typeReference?.type
        is RsStructItem -> declaredType
        is RsEnumVariant -> parentEnum.declaredType
        is RsPatBinding -> type
        else -> null
    }

fun LookupElementBuilder.withPriority(priority: Double): LookupElement =
    if (priority == DEFAULT_PRIORITY) this else PrioritizedLookupElement.withPriority(this, priority)

fun LookupElementBuilder.toRsLookupElement(properties: RsLookupElementProperties): LookupElement {
    return RsLookupElement(this, properties)
}

fun LookupElementBuilder.toKeywordElement(keywordKind: KeywordKind = KeywordKind.KEYWORD): LookupElement =
    toRsLookupElement(RsLookupElementProperties(keywordKind = keywordKind))

private fun RsElement.getLookupElementBuilder(scopeName: String, subst: Substitution): LookupElementBuilder {
    val base = LookupElementBuilder.createWithSmartPointer(scopeName, this)
        .withIcon(if (this is RsFile) RsIcons.MODULE else this.getIcon(0))
        .withStrikeoutness(this is RsDocAndAttributeOwner && queryAttributes.deprecatedAttribute != null)

    return when (this) {
        is RsMod -> if (scopeName == "self" || scopeName == "super" || scopeName == "crate") {
            base.withTailText("::")
        } else {
            base
        }

        is RsConstant -> base
            .withTypeText(typeReference?.getStubOnlyText(subst))
        is RsConstParameter -> base
            .withTypeText(typeReference?.getStubOnlyText(subst))
        is RsFieldDecl -> base
            .bold()
            .withTypeText(typeReference?.getStubOnlyText(subst))
        is RsTraitItem -> base

        is RsFunction -> base
            .withTypeText(retType?.typeReference?.getStubOnlyText(subst) ?: "()")
            .withTailText(valueParameterList?.getStubOnlyText(subst) ?: "()")
            .appendTailText(getExtraTailText(subst), true)

        is RsStructItem -> base
            .withTailText(getFieldsOwnerTailText(this, subst))

        is RsEnumVariant -> base
            .withTypeText(stubAncestorStrict<RsEnumItem>()?.name ?: "")
            .withTailText(getFieldsOwnerTailText(this, subst))

        is RsPatBinding -> base
            .withTypeText(type.let {
                when (it) {
                    is TyUnknown -> ""
                    else -> it.toString()
                }
            })

        is RsMacroBinding -> base.withTypeText(fragmentSpecifier)

        is RsMacroDefinitionBase -> base.withTailText("!")

        else -> base
    }
}

private fun getFieldsOwnerTailText(owner: RsFieldsOwner, subst: Substitution): String = when {
    owner.blockFields != null -> " { ... }"
    owner.tupleFields != null ->
        owner.positionalFields.joinToString(prefix = "(", postfix = ")") { it.typeReference.getStubOnlyText(subst) }
    else -> ""
}

open class RsDefaultInsertHandler : InsertHandler<LookupElement> {

    final override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val element = item.psiElement as? RsElement ?: return
        val scopeName = item.lookupString
        handleInsert(element, scopeName, context, item)
    }

    protected open fun handleInsert(
        element: RsElement,
        scopeName: String,
        context: InsertionContext,
        item: LookupElement
    ) {
        val document = context.document
        val startOffset = context.startOffset
        val curUseItem = context.getElementOfType<RsUseItem>()
        if (element is RsNameIdentifierOwner && !RsNamesValidator.isIdentifier(scopeName) && scopeName !in CAN_NOT_BE_ESCAPED) {
            document.insertString(startOffset, RS_RAW_PREFIX)
        }

        if (element is RsGenericDeclaration) {
            addGenericTypeCompletion(element, document, context)
        }

        when (element) {

            is RsMod -> {
                when (scopeName) {
                    "self",
                    "super" -> {
                        val inSelfParam = context.getElementOfType<RsSelfParameter>() != null
                        if (!(context.isInUseGroup || inSelfParam)) {
                            context.addSuffix("::")
                        }
                    }
                    "crate" -> context.addSuffix("::")
                }
            }

            is RsConstant -> appendSemicolon(context, curUseItem)
            is RsTraitItem -> appendSemicolon(context, curUseItem)
            is RsStructItem -> appendSemicolon(context, curUseItem)

            is RsFunction -> {
                if (curUseItem != null) {
                    appendSemicolon(context, curUseItem)
                } else {
                    val isMethodCall = context.getElementOfType<RsMethodOrField>() != null
                    if (!context.alreadyHasCallParens) {
                        document.insertString(context.selectionEndOffset, "()")
                        context.doNotAddOpenParenCompletionChar()
                    }
                    val caretShift = if (element.valueParameters.isEmpty() && (isMethodCall || !element.hasSelfParameters)) 2 else 1
                    EditorModificationUtil.moveCaretRelatively(context.editor, caretShift)
                    if (element.valueParameters.isNotEmpty()) {
                        AutoPopupController.getInstance(element.project)?.autoPopupParameterInfo(context.editor, element)
                    }
                }
            }

            is RsEnumVariant -> {
                if (curUseItem == null) {
                    // Currently this works only for enum variants (and not for structs). It's because in the case of
                    // struct you may want to append an associated function call after a struct name (e.g. `::new()`)
                    val (text, shift) = when {
                        element.tupleFields != null -> {
                            context.doNotAddOpenParenCompletionChar()
                            Pair("()", 1)
                        }
                        element.blockFields != null -> Pair(" {}", 2)
                        else -> Pair("", 0)
                    }
                    if (!(context.alreadyHasStructBraces || context.alreadyHasCallParens)) {
                        document.insertString(context.selectionEndOffset, text)
                    }
                    EditorModificationUtil.moveCaretRelatively(context.editor, shift)
                }
            }

            is RsMacroDefinitionBase -> {
                if (curUseItem == null) {
                    var caretShift = 2
                    if (!context.nextCharIs('!')) {
                        val braces = element.preferredBraces
                        val text = buildString {
                            append("!")
                            if (braces == MacroBraces.BRACES) {
                                append(" ")
                                caretShift = 3
                            }
                            append(braces.openText)
                            append(braces.closeText)
                        }
                        document.insertString(context.selectionEndOffset, text)
                    }
                    EditorModificationUtil.moveCaretRelatively(context.editor, caretShift)
                } else {
                    appendSemicolon(context, curUseItem)
                }
            }
        }
    }
}

private fun appendSemicolon(context: InsertionContext, curUseItem: RsUseItem?) {
    if (curUseItem != null) {
        val hasSemicolon = curUseItem.lastChild!!.elementType == RsElementTypes.SEMICOLON
        if (!(hasSemicolon || context.isInUseGroup)) {
            context.addSuffix(";")
        }
    }
}

private fun addGenericTypeCompletion(element: RsGenericDeclaration, document: Document, context: InsertionContext) {
    // complete only types that have at least one generic parameter without a default
    if (element.typeParameters.all { it.typeReference != null } &&
        element.constParameters.all { it.expr != null }) return

    // complete angle brackets only in a type context
    val path = context.getElementOfType<RsPath>()
    if (path == null || path.parent !is RsTypeReference) return

    if (element.isFnLikeTrait) {
        if (!context.alreadyHasCallParens) {
            document.insertString(context.selectionEndOffset, "()")
            context.doNotAddOpenParenCompletionChar()
        }
    } else {
        if (!context.alreadyHasAngleBrackets) {
            document.insertString(context.selectionEndOffset, "<>")
        }
    }

    EditorModificationUtil.moveCaretRelatively(context.editor, 1)
}

// When a user types `(` while completion,
// `com.intellij.codeInsight.completion.DefaultCharFilter` invokes completion with selected item.
// And if we insert `()` for the item (for example, function), a user get double parentheses
private fun InsertionContext.doNotAddOpenParenCompletionChar() {
    if (completionChar == '(') {
        setAddCompletionChar(false)
        Testmarks.DoNotAddOpenParenCompletionChar.hit()
    }
}

inline fun <reified T : PsiElement> InsertionContext.getElementOfType(strict: Boolean = false): T? =
    PsiTreeUtil.findElementOfClassAtOffset(file, tailOffset - 1, T::class.java, strict)

private val InsertionContext.isInUseGroup: Boolean
    get() = getElementOfType<RsUseGroup>() != null

val InsertionContext.alreadyHasCallParens: Boolean
    get() = nextCharIs('(')

private val InsertionContext.alreadyHasAngleBrackets: Boolean
    get() = nextCharIs('<')

private val InsertionContext.alreadyHasStructBraces: Boolean
    get() = nextCharIs('{')

private val RsElement.isFnLikeTrait: Boolean
    get() {
        val knownItems = knownItems
        return this == knownItems.Fn ||
            this == knownItems.FnMut ||
            this == knownItems.FnOnce
    }

private fun RsFunction.getExtraTailText(subst: Substitution): String {
    val traitRef = stubAncestorStrict<RsImplItem>()?.traitRef ?: return ""
    return " of ${traitRef.getStubOnlyText(subst)}"
}

fun InsertionContext.nextCharIs(c: Char): Boolean =
    document.charsSequence.indexOfSkippingSpace(c, tailOffset) != null

private fun CharSequence.indexOfSkippingSpace(c: Char, startIndex: Int): Int? {
    for (i in startIndex until this.length) {
        val currentChar = this[i]
        if (c == currentChar) return i
        if (currentChar != ' ' && currentChar != '\t') return null
    }
    return null
}

private val RsElement.canBeExported: Boolean
    get() {
        if (this is RsEnumVariant) return true
        val context = PsiTreeUtil.getContextOfType(this, true, RsItemElement::class.java, RsFile::class.java)
        return context == null || context is RsMod
    }

private fun isCompatibleTypes(lookup: ImplLookup, actualTy: Ty?, expectedType: ExpectedType?): Boolean {
    if (actualTy == null || expectedType == null) return false
    val expectedTy = expectedType.ty
    if (
        actualTy is TyUnknown || expectedTy is TyUnknown ||
        actualTy is TyTypeParameter || expectedTy is TyTypeParameter
    ) return false

    // Replace `TyUnknown` and `TyTypeParameter` with `TyNever` to ignore them when combining types
    val folder = object : TypeFolder {
        override fun foldTy(ty: Ty): Ty = when (ty) {
            is TyUnknown -> TyNever
            is TyTypeParameter -> TyNever
            else -> ty.superFoldWith(this)
        }
    }

    // TODO coerce
    val ty1 = actualTy.foldWith(folder)
    val ty2 = expectedTy.foldWith(folder)
    return if (expectedType.coercable) {
        lookup.ctx.tryCoerce(ty1, ty2)
    } else {
        lookup.ctx.combineTypesNoVars(ty1, ty2)
    }.isOk
}

object Testmarks {
    object DoNotAddOpenParenCompletionChar : Testmark()
}
