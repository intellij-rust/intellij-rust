package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.util.childOfType

object RustElementFactory {
    fun createExpression(project: Project, expression: String): RustExprElement? =
        createFromText(project, "fn main() { $expression; }")

    fun createReference(project: Project, type: String, mutable: Boolean): RustRefTypeElement? =
        createFromText(project, "fn main() { let a: &${if (mutable) "mut " else ""}$type; }")

    fun createStatement(project: Project, statement: String): RustStmtElement? =
        createFromText(project, "fn main() { $statement 92; }")

    fun createModDeclItem(project: Project, modName: String): RustModDeclItemElement? =
        createFromText(project, "mod $modName;")

    fun createOuterAttr(project: Project, attrContents: String): RustOuterAttrElement? =
        createFromText(project, "#[$attrContents] struct Dummy;")

    fun createUseItem(project: Project, path: String): RustUseItemElement? =
        createFromText(project, "use $path;")

    fun createStructExprBody(project: Project, fieldNames: List<String>): RustStructExprBodyElement? {
        val fields = fieldNames.map { "$it: ()," }.joinToString("\n")
        return createFromText(project, "fn main() { S { $fields }; }")
    }

    fun createVariableDeclaration(project: Project, name: String, expr: PsiElement): RustLetDeclElement =
        checkNotNull(createFromText(project, "fn main() {let $name = ${expr.text}; }"))

    fun createMutableVariableDeclaration(project: Project, name: String, expr: PsiElement): RustLetDeclElement =
        checkNotNull(createFromText(project, "fn main() {let mut $name = ${expr.text}; }"))

    fun createVariableDeclarationFromStmt(project: Project, name: String, expr: RustExprStmtElement): RustLetDeclElement =
        checkNotNull(createFromText(project, "fn main() {let $name = ${expr.text} }"))

    fun createBlockExpr(project: Project, body: String): RustBlockExprElement? =
        createFromText(project, "fn main() { { $body } }")

    fun createImplItem(project: Project, traitMethods: List<RustTraitMethodMemberElement>): RustImplItemElement? {
        val m = traitMethods.first()
        m.copy()
        val methods = traitMethods
            .mapNotNull { " ${it.signatureText} {\nunimplemented!()\n}" }
            .joinToString("\n\n")
        return createFromText(project, "impl T for S { $methods }")
    }

    fun createImplItem(project: Project, name: String): RustImplItemElement? =
        createFromText(project, "impl $name {  }")

    fun createIdentifier(project: Project, name: String): PsiElement =
        createFromText<RustModDeclItemElement>(project, "mod $name;")!!.identifier

    fun createWhereClause(project: Project, lifetimeBounds: List<RustLifetimeParamElement>, typeBounds: List<RustTypeParamElement>): RustWhereClauseElement? {
        val lifetimes = lifetimeBounds
            .filter { it.lifetimeParamBounds != null }
            .mapNotNull { it.text }
        val typeConstraints = typeBounds
            .filter { it.typeParamBounds != null }
            .mapNotNull { it.text }
        val whereClauseConstraints = (lifetimes.asIterable() + typeConstraints.asIterable()).joinToString(", ")
        return createFromText(project, "fn main() where $whereClauseConstraints {}")
    }

    private inline fun <reified T : RustCompositeElement> createFromText(project: Project, code: String): T? =
        PsiFileFactory.getInstance(project)
            .createFileFromText("DUMMY.rs", RustLanguage, code)
            ?.childOfType<T>()

    private val RustTraitMethodMemberElement.signatureText: String? get() {
        // We can't simply take a substring of original method declaration
        // because of anonymous parameters.
        val name = name ?: return null
        val generics = genericParams?.text ?: ""

        val parameters = parameters ?: return null
        val allArguments = listOfNotNull(parameters.selfArgument?.text) + parameters.parameterList.map {
            // fix possible anon parameter
            "${it.pat?.text ?: "_"}: ${it.type?.text ?: "()"}"
        }

        val ret = retType?.text ?: ""
        val where = whereClause?.text ?: ""
        return "fn $name $generics (${allArguments.joinToString(",")}) $ret $where"
    }
}
