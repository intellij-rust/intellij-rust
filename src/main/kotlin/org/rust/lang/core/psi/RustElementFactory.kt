package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.util.childOfType

object RustElementFactory {
    fun createExpression(project: Project, expression: String): RustExprElement? =
        createFromText(project, "fn main() {$expression;}")

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

    private inline fun <reified T : RustCompositeElement> createFromText(project: Project, code: String): T? =
        PsiFileFactory.getInstance(project)
            .createFileFromText("DUMMY.rs", RustLanguage, code)
            ?.childOfType<T>()
}
