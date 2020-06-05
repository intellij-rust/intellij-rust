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
import org.rust.lang.core.psi.ext.EqualityOp
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.ty.TyPointer
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type

abstract class AssertPostfixTemplateBase(
    name: String,
    provider: RsPostfixTemplateProvider
) : StringBasedPostfixTemplate(name, "$name!(exp);", RsTopMostInScopeSelector(RsExpr::isBool), provider) {

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
 * Base class for postfix templates that just add prefix/suffix to expression text.
 *
 * Note, `example` param should contain `expr` substring
 */
abstract class SimpleExprPostfixTemplate(
    name: String,
    example: String,
    provider: RsPostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector = RsTopMostInScopeSelector()
): StringBasedPostfixTemplate(name, example, selector, provider) {

    init {
        require("expr" in example) {
            "Template example should contain `expr`"
        }
    }

    override fun getTemplateString(element: PsiElement): String = example.replace("expr", element.text)
    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class LambdaPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("lambda", "|| expr", provider)

class NotPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("not", "!expr", provider)

class RefPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("ref", "&expr", provider)
class RefmPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("refm", "&mut expr", provider)

class DerefPostfixTemplate(provider: RsPostfixTemplateProvider) :
    SimpleExprPostfixTemplate(
        "deref",
        "*expr",
        provider,
        RsTopMostInScopeSelector {
            it.type is TyReference || it.type is TyPointer || it.implementsDeref
        }
    )

class MatchPostfixTemplate(provider: RsPostfixTemplateProvider) :
    StringBasedPostfixTemplate("match", "match expr {...}", RsTopMostInScopeSelector(), provider) {

    override fun getTemplateString(element: PsiElement): String = "match ${element.text} {\n\$PAT\$ => {\$END\$}\n}"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr

    override fun setVariables(template: Template, element: PsiElement) {
        with(template) {
            addVariable("PAT", TextExpression("_"), true)
        }
    }
}

class IterPostfixTemplate(name: String, provider: RsPostfixTemplateProvider) :
    StringBasedPostfixTemplate(
        name,
        "for x in expr",
        RsTopMostInScopeSelector { it.isIntoIterator },
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

class SomePostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("some", "Some(expr)", provider)
class OkPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("ok", "Ok(expr)", provider)
class ErrPostfixTemplate(provider: RsPostfixTemplateProvider) : SimpleExprPostfixTemplate("err", "Err(expr)", provider)

private val RsExpr.isIntoIterator: Boolean
    get() = implLookup.isIntoIterator(type)

private val RsExpr.implementsDeref: Boolean
    get() = implLookup.isDeref(this.type)
