/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateResultListener
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.macro.CompleteMacro
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.parser.RustParserUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.EqualityOp
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.ty.TyPointer
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type
import org.rust.openapiext.createSmartPointer

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
 * Base class for postfix templates that just add prefix/suffix to expression text.
 *
 * Note, `example` param should contain `expr` substring
 */
abstract class SimpleExprPostfixTemplate(
    name: String,
    example: String,
    provider: RsPostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector = RsExprParentsSelector()
) : StringBasedPostfixTemplate(name, example, selector, provider) {

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

class WrapTypePathPostfixTemplate(provider: RsPostfixTemplateProvider) : StringBasedPostfixTemplate("wrap", "\$wrapper$<path>", RsTypeParentsSelector(), provider) {
    override fun getTemplateString(element: PsiElement): String = example.replace("path", element.text)

    override fun expandForChooseExpression(element: PsiElement, editor: Editor) {
        val typeRef = element as? RsTypeReference ?: return

        val factory = RsPsiFactory(typeRef.project)
        val path = factory.tryCreatePath("Wrapper<${typeRef.text}>", RustParserUtil.PathParsingMode.TYPE) ?: return
        val newTypeRef = factory.tryCreateType(path.text) ?: return
        val inserted = typeRef.replace(newTypeRef) as? RsBaseType ?: return
        val ptr = inserted.createSmartPointer()

        val template = editor.newTemplateBuilder(inserted) ?: return
        val name = inserted.path?.referenceNameElement ?: return
        template.replaceElement(name, MacroCallNode(CompleteMacro()))
        template.withResultListener {
            if (it != TemplateResultListener.TemplateResult.Canceled) {
                // Move caret after the inserted wrapper type
                val end = ptr.element?.path?.typeArgumentList?.gt
                if (end != null) {
                    editor.caretModel.moveToOffset(end.startOffset + 1)
                }
            }
        }
        template.runInline()
    }
}

private val RsExpr.isIntoIterator: Boolean
    get() = implLookup.isIntoIterator(type)

private val RsExpr.implementsDeref: Boolean
    get() = implLookup.isDeref(this.type)
