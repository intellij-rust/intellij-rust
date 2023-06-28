/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.psi.ext.EqualityOp
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.toTypeSubst
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

abstract class AssertPostfixTemplateBase(
    name: String,
    provider: RsPostfixTemplateProvider
) : StringBasedPostfixTemplate(name, "$name!(exp);", RsExprParentsSelector(RsExpr::isBool), provider) {

    override fun getTemplateString(element: PsiElement): String =
        if (element is RsBinaryExpr && element.operatorType == EqualityOp.EQ) {
            "${this.presentableName}_eq!(${element.left.text}, ${element.right?.text});\$END$"
        } else {
            "$presentableName!(${element.text});\$END$"
        }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class AssertPostfixTemplate(provider: RsPostfixTemplateProvider) : AssertPostfixTemplateBase("assert", provider)
class DebugAssertPostfixTemplate(provider: RsPostfixTemplateProvider) : AssertPostfixTemplateBase("debug_assert", provider)

/**
 * Base class for postfix templates that just add prefix/suffix to some element text.
 *
 * Note, `example` param should contain `placeholder` substring.
 */
abstract class SimplePostfixTemplate(
    name: String,
    example: String,
    provider: RsPostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector,
    private val placeholder: String = "expr"
) : StringBasedPostfixTemplate(name, example, selector, provider) {

    init {
        require(placeholder in example) {
            "Template example should contain `${placeholder}`"
        }
    }

    override fun getTemplateString(element: PsiElement): String = example.replace(placeholder, element.text)
    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

abstract class SimpleExprPostfixTemplate(
    name: String,
    example: String,
    provider: RsPostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector = RsExprParentsSelector(),
) : SimplePostfixTemplate(name, example, provider, selector, placeholder = "expr")

abstract class SimpleTypePostfixTemplate(
    name: String,
    example: String,
    provider: RsPostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector = RsTypeParentsSelector(),
) : SimplePostfixTemplate(name, example, provider, selector, placeholder = "type")

class LambdaPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("lambda", "|| expr", provider)

class NotPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("not", "!expr", provider)

class RefExprPostfixTemplate(provider: RsPostfixTemplateProvider)
    : SimpleExprPostfixTemplate("ref", "&expr", provider)
class RefmExprPostfixTemplate(provider: RsPostfixTemplateProvider)
    : SimpleExprPostfixTemplate("refm", "&mut expr", provider)

class RefTypePostfixTemplate(provider: RsPostfixTemplateProvider)
    : SimpleTypePostfixTemplate("ref", "&type", provider)
class RefmTypePostfixTemplate(provider: RsPostfixTemplateProvider)
    : SimpleTypePostfixTemplate("refm", "&mut type", provider)

class DerefPostfixTemplate(provider: RsPostfixTemplateProvider) :
    SimpleExprPostfixTemplate(
        "deref",
        "*expr",
        provider,
        RsExprParentsSelector {
            it.type is TyReference || it.type is TyPointer || it.implementsDeref
        }
    )

class IterPostfixTemplate(name: String, provider: RsPostfixTemplateProvider) :
    StringBasedPostfixTemplate(
        name,
        "for x in expr",
        RsExprParentsSelector { it.isIntoIterator },
        provider
    ) {
    override fun getTemplateString(element: PsiElement): String =
        "for \$name$ in ${element.text} {\n     \$END$\n}"

    override fun setVariables(template: Template, element: PsiElement) {
        template.addVariable("name", TextExpression("x"), true)
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class DbgPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("dbg", "dbg!(expr)", provider)
class DbgrPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("dbgr", "dbg!(&expr)", provider)

class SomePostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("some", "Some(expr)", provider)
class OkPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("ok", "Ok(expr)", provider)
class ErrPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("err", "Err(expr)", provider)

class SlicePostfixTemplate(name: String, provider: RsPostfixTemplateProvider) :
    StringBasedPostfixTemplate(
        name,
        "&list[i..j]",
        RsExprParentsSelector {
            val rangeType = it.knownItems.Range?.declaredType ?: return@RsExprParentsSelector false
            val idx = rangeType.getTypeParameter("Idx") ?: return@RsExprParentsSelector false
            val subst = mapOf(idx to TyInteger.USize.INSTANCE).toTypeSubst()
            pred(it.type, rangeType.substitute(subst), it.implLookup)
        },
        provider
    ) {

    override fun getTemplateString(element: PsiElement): String {
        val isRef = element is RsUnaryExpr && element.operatorType in listOf(UnaryOperator.REF, UnaryOperator.REF_MUT)
        val prefix = if (isRef) "" else "&"
        return "$prefix${element.text}[\$from\$..\$to\$]\$END\$"
    }

    override fun setVariables(template: Template, element: PsiElement) {
        template.addVariable("from", TextExpression("i"), true)
        template.addVariable("to", TextExpression("j"), true)
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr

    companion object {
        private fun pred(ty: Ty, indexTy: Ty, implLookup: ImplLookup): Boolean =
            when (ty) {
                is TyStr, is TySlice -> true
                is TyReference -> pred(ty.referenced, indexTy, implLookup)
                else -> implLookup.isIndex(ty, indexTy).isTrue
            }
    }
}

private val RsExpr.isIntoIterator: Boolean
    get() = implLookup.isIntoIterator(type).isTrue

private val RsExpr.implementsDeref: Boolean
    get() = implLookup.isDeref(this.type).isTrue
