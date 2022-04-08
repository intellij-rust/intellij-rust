/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ml

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import org.rust.ide.utils.import.isStd
import org.rust.lang.core.completion.RsLookupElement
import org.rust.lang.core.completion.sort.RS_COMPLETION_WEIGHERS
import org.rust.lang.core.completion.sort.RsCompletionWeigher
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import kotlin.reflect.KClass

/**
 * Note that any (Boolean, Number or Enum) value returned from [RsCompletionWeigher] (see [RS_COMPLETION_WEIGHERS])
 * is automatically added as an "element feature" ([RsCompletionWeigher.id] is used as a key),
 * so there is no sense to add it again in [calculateFeatures].
 *
 * Also note that there is a common feature provider for all languages:
 * [com.intellij.completion.ml.common.CommonElementLocationFeatures].
 *
 * @see RsContextFeatureProvider
 */
@Suppress("UnstableApiUsage")
class RsElementFeatureProvider : ElementFeatureProvider {
    override fun getName(): String = "rust"

    override fun calculateFeatures(
        element: LookupElement,
        location: CompletionLocation,
        contextFeatures: ContextFeatures
    ): Map<String, MLFeatureValue> {
        val result = hashMapOf<String, MLFeatureValue>()
        val lookupString = element.lookupString

        /** If [element] is a keyword, store keyword kind as [KIND] feature and finish */
        val keywordKind = RsKeywordMLKind.from(lookupString)
        if (keywordKind != null) {
            result[KIND] = MLFeatureValue.categorical(keywordKind)
            return result
        }

        /**
         * Otherwise, if [element] is [RsElement],
         * store PSI kind as [KIND] feature
         * and store if its origin is stdlib as [IS_FROM_STDLIB] feature
         */
        val psiElement = element.psiElement as? RsElement ?: return result
        val psiElementKind = RsPsiElementMLKind.from(psiElement)
        if (psiElementKind != null) {
            result[KIND] = MLFeatureValue.categorical(psiElementKind)
        }
        val containingCrate = psiElement.containingMod.containingCrate
        if (containingCrate != null) {
            result[IS_FROM_STDLIB] = MLFeatureValue.binary(containingCrate.isStd)
        }

        val rsElement = element.`as`(RsLookupElement::class.java)
        if (rsElement != null) {
            result[IS_OPERATOR_METHOD] = MLFeatureValue.binary(rsElement.props.isOperatorMethod)
            result[IS_BLANKET_IMPL_MEMBER] = MLFeatureValue.binary(rsElement.props.isBlanketImplMember)
            result[IS_UNSAFE_FN] = MLFeatureValue.binary(rsElement.props.isUnsafeFn)
            result[IS_ASYNC_FN] = MLFeatureValue.binary(rsElement.props.isAsyncFn)
            result[IS_CONST_FN_OR_CONST] = MLFeatureValue.binary(rsElement.props.isConstFnOrConst)
            result[IS_EXTERN_FN] = MLFeatureValue.binary(rsElement.props.isExternFn)
        }

        return result
    }

    companion object {
        private const val KIND: String = "kind"
        private const val IS_FROM_STDLIB: String = "is_from_stdlib"
        private const val IS_OPERATOR_METHOD: String = "is_operator_method"
        private const val IS_BLANKET_IMPL_MEMBER: String = "is_blanket_impl_member"
        private const val IS_UNSAFE_FN: String = "is_unsafe_fn"
        private const val IS_ASYNC_FN: String = "is_async_fn"
        private const val IS_CONST_FN_OR_CONST: String = "is_const_fn_or_const"
        private const val IS_EXTERN_FN: String = "is_extern_fn"
    }
}

/** Should be synchronized with [org.rust.lang.core.psi.RsTokenTypeKt#RS_KEYWORDS] */
internal enum class RsKeywordMLKind(val lookupString: String) {
    As("as"), Async("async"), Auto("auto"),
    Box("box"), Break("break"),
    Const("const"), Continue("continue"), Crate("crate"), CSelf("Self"),
    Default("default"), Dyn("dyn"),
    Else("else"), Enum("enum"), Extern("extern"),
    Fn("fn"), For("for"),
    If("if"), Impl("impl"), In("in"),
    Macro("macro"),
    Let("let"), Loop("loop"),
    Match("match"), Mod("mod"), Move("move"), Mut("mut"),
    Pub("pub"),
    Raw("raw"), Ref("ref"), Return("return"),
    Self("self"), Static("static"), Struct("struct"), Super("super"),
    Trait("trait"), Type("type"),
    Union("union"), Unsafe("unsafe"), Use("use"),
    Where("where"), While("while"),
    Yield("yield");

    companion object {
        fun from(lookupString: String): RsKeywordMLKind? {
            return values().find { it.lookupString == lookupString }
        }
    }
}

@Suppress("unused")
internal enum class RsPsiElementMLKind(val klass: KClass<out RsElement>) {
    PatBinding(RsPatBinding::class),
    Function(RsFunction::class),
    StructItem(RsStructItem::class),
    TraitItem(RsTraitItem::class),
    NamedFieldDecl(RsNamedFieldDecl::class),
    File(RsFile::class),
    EnumVariant(RsEnumVariant::class),
    SelfParameter(RsSelfParameter::class),
    Macro(RsMacro::class),
    EnumItem(RsEnumItem::class),
    TypeAlias(RsTypeAlias::class),
    LifetimeParameter(RsLifetimeParameter::class),
    Constant(RsConstant::class),
    ModItem(RsModItem::class),
    TupleFieldDecl(RsTupleFieldDecl::class),
    PathExpr(RsPathExpr::class),
    DotExpr(RsDotExpr::class),
    BaseType(RsBaseType::class),
    PatIdent(RsPatIdent::class),
    UseSpeck(RsUseSpeck::class),
    ImplItem(RsImplItem::class),
    StructLiteralBody(RsStructLiteralBody::class),
    MacroArgument(RsMacroArgument::class),
    MetaItem(RsMetaItem::class),
    BlockFields(RsBlockFields::class),
    TraitRef(RsTraitRef::class),
    ValueArgumentList(RsValueArgumentList::class),
    Path(RsPath::class),
    FormatMacroArg(RsFormatMacroArg::class),
    MacroCall(RsMacroCall::class),
    StructLiteral(RsStructLiteral::class),
    RefLikeType(RsRefLikeType::class),
    TypeArgumentList(RsTypeArgumentList::class);

    companion object {
        fun from(element: RsElement): RsPsiElementMLKind? {
            return values().find { it.klass.isInstance(element) }
        }
    }
}
