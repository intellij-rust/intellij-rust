package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiParserFacade
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.childOfType
import org.rust.lang.core.psi.ext.selfParameter
import org.rust.lang.core.psi.ext.valueParameters

class RsPsiFactory(private val project: Project) {
    fun createSelf(mutable: Boolean = false): RsSelfParameter {
        return createFromText<RsFunction>("fn main(&${if (mutable) "mut " else ""}self){}")?.selfParameter
            ?: error("Failed to create self element")
    }

    fun createIdentifier(text: String): PsiElement =
        createFromText<RsModDeclItem>("mod $text;")?.identifier
            ?: error("Failed to create identifier: `$text`")

    fun createQuoteIdentifier(text: String): PsiElement =
        createFromText<RsLifetimeDecl>("fn foo<$text>(_: &$text u8) {}")?.quoteIdentifier
            ?: error("Failed to create quote identifier: `$text`")

    fun createExpression(text: String): RsExpr =
        createFromText("fn main() { $text; }")
            ?: error("Failed to create expression from text: `$text`")

    fun createTryExpression(expr: RsExpr): RsTryExpr {
        val newElement = createExpressionOfType<RsTryExpr>("a?")
        newElement.expr.replace(expr)
        return newElement
    }

    fun createIfExpression(condition: RsExpr, thenBranch: RsExpr): RsIfExpr {
        val result = createExpressionOfType<RsIfExpr>("if ${condition.text} { () }")
        val block = result.block!!
        if (thenBranch is RsBlockExpr) {
            block.replace(thenBranch.block)
        } else {
            block.expr!!.replace(thenBranch)
        }
        return result
    }


    fun createBlockExpr(body: String): RsBlockExpr =
        createExpressionOfType("{ $body }")

    fun createUnsafeBlockExpr(body: String): RsBlockExpr =
        createExpressionOfType("unsafe { $body }")

    fun tryCreatePathExpr(text: String): RsPathExpr? {
        val expr = createFromText<RsPathExpr>("fn main() { $text;}") ?: return null
        if (expr.text != text) return null
        return expr
    }

    fun createStructLiteralField(name: String): RsStructLiteralField =
        createExpressionOfType<RsStructLiteral>("S { $name: () }").structLiteralBody.structLiteralFieldList[0]

    fun createStatement(text: String): RsStmt =
        createFromText("fn main() { $text 92; }")
            ?: error("Failed to create statement from text: `$text`")

    fun createLetDeclaration(name: String, expr: RsExpr, mutable: Boolean = false, type: RsTypeReference? = null): RsLetDecl =
        createStatement("let ${if (mutable) "mut " else ""}$name${if (type != null) ": ${type.text}" else ""} = ${expr.text};") as RsLetDecl


    fun createType(text: String): RsTypeReference =
        createFromText("fn main() { let a : $text; }")
            ?: error("Failed to create type from text: `$text`")

    fun createMethodParam(text: String): PsiElement {
        val fnItem: RsFunction = createTraitMethodMember("fn foo($text);")
        return fnItem.selfParameter ?: fnItem.valueParameters.firstOrNull()
            ?: error("Failed to create type from text: `$text`")
    }

    fun createReferenceType(innerTypeText: String, mutable: Boolean): RsRefLikeType =
        createType("&${if (mutable) "mut " else ""}$innerTypeText") as RsRefLikeType

    fun createModDeclItem(modName: String): RsModDeclItem =
        createFromText("mod $modName;")
            ?: error("Failed to crate mod decl with name: `$modName`")

    fun createUseItem(text: String): RsUseItem =
        createFromText("use $text;")
            ?: error("Failed to create use item from text: `$text`")

    fun createTraitImplItem(traitMethods: List<RsFunction>, traitTypeAliases: List<RsTypeAlias>, traitConstants: List<RsConstant>): RsImplItem {
        val members = (
            traitConstants.map { "    const ${it.identifier.text}: ${it.typeReference?.text} = unimplemented!();" } +
                traitTypeAliases.map { "    type ${it.name} = ();" }
            ).joinToString("\n")

        val functions = traitMethods.map { "    ${it.signatureText ?: ""}{\n        unimplemented!()\n    }" }.joinToString("\n\n")
        val text = "impl T for S {\n${when {
            members.isEmpty() -> functions
            functions.isEmpty() -> members
            else -> members + "\n\n" + functions
        }}\n}"
        return createFromText(text)
            ?: error("Failed to create an impl from text: `$text`")
    }

    fun createTraitMethodMember(text: String): RsFunction {
        val traitImpl: RsTraitItem = createFromText("trait Foo { $text }") ?:
            error("Failed to create an method member from text: `$text`")
        return traitImpl.functionList.first()
    }

    fun createInherentImplItem(name: String, typeParameterList: RsTypeParameterList?, whereClause: RsWhereClause?): RsImplItem {
        val whereText = whereClause?.text ?: ""
        val typeParameterListText = typeParameterList?.text ?: ""
        val typeArgumentListText = if (typeParameterList == null) {
            ""
        } else {
            val parameterNames = typeParameterList.lifetimeParameterList.map { it.lifetimeDecl.text } +
                typeParameterList.typeParameterList.map { it.name }
            parameterNames.joinToString(", ", "<", ">")
        }

        return createFromText("impl $typeParameterListText $name $typeArgumentListText $whereText {  }")
            ?: error("Failed to create an inherent impl with name: `$name`")
    }

    fun createWhereClause(
        lifetimeBounds: List<RsLifetimeParameter>,
        typeBounds: List<RsTypeParameter>
    ): RsWhereClause {

        val lifetimes = lifetimeBounds
            .filter { it.lifetimeParamBounds != null }
            .mapNotNull { it.text }

        val typeConstraints = typeBounds
            .filter { it.typeParamBounds != null }
            .mapNotNull { it.text }

        val whereClauseConstraints = (lifetimes + typeConstraints).joinToString(", ")

        val text = "where $whereClauseConstraints"
        return createFromText("fn main() $text {}")
            ?: error("Failed to create a where clause from text: `$text`")
    }

    fun createTypeParameterList(
        params: Iterable<String>
    ): RsTypeParameterList {
        val text = params.joinToString(prefix = "<", separator = ", ", postfix = ">")

        return createFromText<RsFunction>("fn foo$text() {}")?.typeParameterList
            ?: error("Failed to create type from text: `$text`")
    }

    fun createOuterAttr(text: String): RsOuterAttr =
        createFromText("#[$text] struct Dummy;")
            ?: error("Failed to create an outer attribute from text: `$text`")

    private inline fun <reified T : RsCompositeElement> createFromText(code: String): T? =
        PsiFileFactory.getInstance(project)
            .createFileFromText("DUMMY.rs", RsFileType, code)
            .childOfType<T>()

    fun createComma(): PsiElement =
        createFromText<RsValueParameter>("fn f(_ : (), )")!!.nextSibling

    fun createColon(): PsiElement =
        createFromText<RsConstant>("const C: () = ();")!!.colon!!

    fun createNewline(): PsiElement =
        PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")

    fun createUnsafe(): PsiElement =
        createFromText<RsFunction>("unsafe fn foo(){}")?.unsafe
            ?: error("Failed to create unsafe element")

    fun createFunction(name: String, stmts: List<PsiElement>, public: Boolean, self: Boolean): RsFunction =
        createFromText<RsFunction>("${if (public) "pub" else ""} fn $name(${if (self) "self" else ""}) {\n" +
            stmts.joinToString(separator = "\n", transform = { it.text }) +
            "\n}")
            ?: error("Failed to create function element: ${name}")

    fun createFunctionCallFunctionStmt(name: String, type: String?): RsStmt =
        createFromText("fn main(){${if (type != null) "$type::" else ""}$name();}")
            ?: error("Failed to create call function statement")

    fun createFunctionCallSelfMethodStmt(name: String): RsStmt =
        createFromText("fn main(){self.$name();}")
            ?: error("Failed to create call method statement")

    fun createImpl(name: String, functions: List<RsFunction>): RsImplItem =
        createFromText<RsImplItem>("impl $name {\n${functions.joinToString(separator = "\n", transform = { it.text })}\n}")
            ?: error("Failed to create RsImplItem element")

    fun createValueParameter(name: String, type: RsTypeReference, mutable: Boolean = false): RsValueParameter {
        return createFromText<RsFunction>("fn main($name: ${if (mutable) "&mut " else ""}${type.text}){}")
            ?.valueParameterList?.valueParameterList?.get(0)
            ?: error("Failed to create parameter element")
    }

    fun createPatBinding(name: String, mutable: Boolean = false): RsPatBinding =
        (createStatement("let ${if (mutable) "mut " else ""}$name = 10;") as RsLetDecl).pat
            ?.firstChild as RsPatBinding?
            ?: error("Failed to create pat element")

    private inline fun <reified E : RsExpr> createExpressionOfType(text: String): E =
        createExpression(text) as? E
            ?: error("Failed to create ${E::class.simpleName} from `$text`")
}

private val RsFunction.signatureText: String? get() {
    // We can't simply take a substring of original method declaration
    // because of anonymous parameters.
    val name = name ?: return null
    val generics = typeParameterList?.text ?: ""

    val allArguments = listOfNotNull(selfParameter?.text) + valueParameters.map {
        // fix possible anon parameter
        "${it.pat?.text ?: "_"}: ${it.typeReference?.text ?: "()"}"
    }

    val ret = retType?.text?.let { it + " " } ?: ""
    val where = whereClause?.text ?: ""
    return "fn $name$generics(${allArguments.joinToString(",")}) $ret$where"
}
