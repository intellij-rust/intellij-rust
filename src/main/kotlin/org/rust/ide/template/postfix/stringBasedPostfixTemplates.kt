/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.EqualityOp
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.StdKnownItems
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.ty.withDefaultSubst
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

class LambdaPostfixTemplate(provider: RsPostfixTemplateProvider) :
    StringBasedPostfixTemplate("lambda", "|| expr", RsTopMostInScopeSelector(), provider) {

    override fun getTemplateString(element: PsiElement): String = "|| ${element.text}"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class NotPostfixTemplate(provider: RsPostfixTemplateProvider) :
    StringBasedPostfixTemplate("not", "!expr", RsTopMostInScopeSelector(), provider) {

    override fun getTemplateString(element: PsiElement): String = "!${element.text}"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class MatchPostfixTemplate(provider: RsPostfixTemplateProvider) :
    StringBasedPostfixTemplate("match", "match expr {...}", RsTopMostInScopeSelector(), provider) {

    override fun getTemplateString(element: PsiElement): String = "match ${element.text} {\n\$PAT\$ => {\$END\$}\n}"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr

    override fun setVariables(template: Template, element: PsiElement): Unit {
        with(template) {
            addVariable("PAT", TextExpression("_"), true)
        }
    }
}
class IterPostfixTemplate(provider: RsPostfixTemplateProvider):
    StringBasedPostfixTemplate("iter","for x in expr", RsTopMostInScopeSelector {
        val items = StdKnownItems.relativeTo(it)
        val iterTrait = items.findIteratorTrait()?: return@RsTopMostInScopeSelector false
        val intoIterTrait = items.findCoreItem("iter::IntoIterator") as?RsTraitItem?: return@RsTopMostInScopeSelector false
        val implLookup = ImplLookup.relativeTo(it)
        implLookup.canSelect(TraitRef(it.type, iterTrait.withDefaultSubst()))
        || implLookup.canSelect(TraitRef(it.type, intoIterTrait.withDefaultSubst()))
    }, provider) {
    override fun getTemplateString(element: PsiElement): String {
        if(element !is RsExpr) return element.text
        val items = StdKnownItems.relativeTo(element)
        val implLookup = ImplLookup.relativeTo(element)
        val iterTrait = items.findIteratorTrait()!! // impossible as it was checked in selector
        val iterText = if (implLookup.canSelect(TraitRef(element.type, iterTrait.withDefaultSubst()))) {
            element.text
        } else {
            element.text + ".into_iter()"
        }
        return "for x in $iterText{\n\$END$\n}"
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}
