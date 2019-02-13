/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiParserFacade
import com.intellij.util.LocalTimeCounter
import org.rust.ide.presentation.insertionSafeText
import org.rust.ide.presentation.insertionSafeTextWithLifetimes
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsPsiFactory.PathNamespace.TYPES
import org.rust.lang.core.psi.RsPsiFactory.PathNamespace.VALUES
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.infer.resolve
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.ty.Mutability.MUTABLE
import org.rust.lang.core.types.type

class RsPsiFactory(private val project: Project, private val markGenerated: Boolean = true) {
    fun createFile(text: CharSequence): RsFile =
        PsiFileFactory.getInstance(project)
            .createFileFromText(
                "DUMMY.rs",
                RsFileType,
                text,
                /*modificationStamp =*/ LocalTimeCounter.currentTime(), // default value
                /*eventSystemEnabled =*/ false, // default value
                /*markAsCopy =*/ markGenerated // `true` by default
            ) as RsFile

    fun createMacroBody(text: String): RsMacroBody? = createFromText(
        "macro_rules! m $text"
    )

    fun createSelf(mutable: Boolean = false): RsSelfParameter {
        return createFromText<RsFunction>("fn main(&${if (mutable) "mut " else ""}self){}")?.selfParameter
            ?: error("Failed to create self element")
    }

    fun createIdentifier(text: String): PsiElement =
        createFromText<RsModDeclItem>("mod ${text.escapeIdentifierIfNeeded()};")?.identifier
            ?: error("Failed to create identifier: `$text`")

    fun createQuoteIdentifier(text: String): PsiElement =
        createFromText<RsLifetimeParameter>("fn foo<$text>(_: &$text u8) {}")?.quoteIdentifier
            ?: error("Failed to create quote identifier: `$text`")

    fun createExpression(text: String): RsExpr =
        tryCreateExpression(text)
            ?: error("Failed to create expression from text: `$text`")

    fun tryCreateExpression(text: CharSequence): RsExpr? =
        createFromText("fn main() { $text; }")

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

    fun createIfElseExpression(condition: RsExpr, thenBlock: RsBlock, elseBlock: RsBlock) : RsIfExpr {
        val resultIfExpr = createExpressionOfType<RsIfExpr>("if ${condition.text} { () } else { () }")
        resultIfExpr.block!!.replace(thenBlock)
        resultIfExpr.elseBranch!!.block!!.replace(elseBlock)

        return resultIfExpr
    }

    fun createBlockExpr(body: CharSequence): RsBlockExpr =
        createExpressionOfType("{ $body }")

    fun createUnsafeBlockExpr(body: String): RsBlockExpr =
        createExpressionOfType("unsafe { $body }")

    fun tryCreatePath(text: String, ns: PathNamespace = TYPES): RsPath? {
        val path = when (ns) {
            TYPES -> createFromText("fn foo(t: $text) {}")
            VALUES -> createFromText<RsPathExpr>("fn main() { $text; }")?.path
        } ?: return null
        if (path.text != text) return null
        return path
    }

    fun createStructLiteral(name: String): RsStructLiteral =
        createExpressionOfType("$name { }")

    fun createStructLiteralField(name: String, value: RsExpr? = null): RsStructLiteralField {
        val structLiteralField = createExpressionOfType<RsStructLiteral>("S { $name: () }")
            .structLiteralBody
            .structLiteralFieldList[0]
        if (value != null) structLiteralField.expr?.replace(value)
        return structLiteralField
    }

    data class BlockField(val pub: Boolean, val name: String, val type: RsTypeReference)

    fun createBlockFields(fields: List<BlockField>): RsBlockFields {
        val fieldsText = fields.joinToString(separator = ",\n") {
            "${"pub".iff(it.pub)}${it.name}: ${it.type.text}"
        }
        return createStruct("struct S { $fieldsText }")
            .blockFields!!
    }

    fun createStruct(text: String): RsStructItem =
        createFromText(text)
            ?: error("Failed to create struct from text: `$text`")

    fun createStatement(text: String): RsStmt =
        createFromText("fn main() { $text 92; }")
            ?: error("Failed to create statement from text: `$text`")

    fun createLetDeclaration(name: String, expr: RsExpr, mutable: Boolean = false, type: RsTypeReference? = null): RsLetDecl =
        createStatement("let ${"mut".iff(mutable)}$name${if (type != null) ": ${type.text}" else ""} = ${expr.text};") as RsLetDecl


    fun createType(text: CharSequence): RsTypeReference =
        tryCreateType(text)
            ?: error("Failed to create type from text: `$text`")

    fun tryCreateType(text: CharSequence): RsTypeReference? =
        createFromText("fn main() { let a : $text; }")

    fun createMethodParam(text: String): PsiElement {
        val fnItem: RsFunction = createTraitMethodMember("fn foo($text);")
        return fnItem.selfParameter ?: fnItem.valueParameters.firstOrNull()
        ?: error("Failed to create method param from text: `$text`")
    }

    fun createReferenceType(innerTypeText: String, mutable: Boolean): RsRefLikeType =
        createType("&${if (mutable) "mut " else ""}$innerTypeText").typeElement as RsRefLikeType

    fun createModDeclItem(modName: String): RsModDeclItem =
        createFromText("mod $modName;")
            ?: error("Failed to create mod decl with name: `$modName`")

    fun createUseItem(text: String): RsUseItem =
        createFromText("use $text;")
            ?: error("Failed to create use item from text: `$text`")

    fun createUseSpeck(text: String): RsUseSpeck =
        createFromText("use $text;")
            ?: error("Failed to create use speck from text: `$text`")

    fun createExternCrateItem(crateName: String): RsExternCrateItem =
        createFromText("extern crate $crateName;")
            ?: error("Failed to create extern crate item from text: `$crateName`")

    fun createModItem(modName: String, modText: String): RsModItem {
        val text = """
            mod $modName {
                $modText
            }"""
        return createFromText(text) ?: error("Failed to create mod item with name: `$modName` from text: `$modText`")
    }

    fun createMembers(members: Collection<RsAbstractable>, subst: Substitution = emptySubstitution): RsMembers {
        val body = members.joinToString(separator = "\n", transform = {
            when (it) {
                is RsConstant ->
                    "    const ${it.identifier.text}: ${it.typeReference?.substAndGetText(subst)} = unimplemented!();"
                is RsTypeAlias ->
                    "    type ${it.name} = ();"
                is RsFunction ->
                    "    ${it.getSignatureText(subst) ?: ""}{\n        unimplemented!()\n    }"
                else ->
                    error("Unknown trait member")
            }
        })

        val text = "impl T for S {$body}"
        return createFromText(text) ?: error("Failed to create an impl from text: `$text`")
    }

    fun createTraitMethodMember(text: String): RsFunction {
        val members: RsMembers = createFromText("trait Foo { $text }")
            ?: error("Failed to create an method member from text: `$text`")
        return members.functionList.first()
    }

    fun createInherentImplItem(name: String, typeParameterList: RsTypeParameterList?, whereClause: RsWhereClause?): RsImplItem {
        val whereText = whereClause?.text ?: ""
        val typeParameterListText = typeParameterList?.text ?: ""
        val typeArgumentListText = if (typeParameterList == null) {
            ""
        } else {
            val parameterNames = typeParameterList.lifetimeParameterList.map { it.quoteIdentifier.text } +
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

        val lifetimeConstraints = lifetimeBounds
            .filter { it.lifetimeParamBounds != null }
            .mapNotNull { it.text }

        val typeConstraints = typeBounds
            .filter { it.typeParamBounds != null }
            .mapNotNull { it.text }

        val whereClauseConstraints = (lifetimeConstraints + typeConstraints).joinToString(", ")

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

    fun createTypeParameterList(
        params: String
    ): RsTypeParameterList {
        return createFromText<RsFunction>("fn foo<$params>() {}")?.typeParameterList
            ?: error("Failed to create type parameters from text: `<$params`>")
    }

    fun createTypeArgumentList(
        params: Iterable<String>
    ): RsTypeArgumentList {
        val text = params.joinToString(prefix = "<", separator = ", ", postfix = ">")
        return createFromText("type T = a$text") ?: error("Failed to create type argument from text: `$text`")
    }

    fun createOuterAttr(text: String): RsOuterAttr =
        createFromText("#[$text] struct Dummy;")
            ?: error("Failed to create an outer attribute from text: `$text`")

    fun createInnerAttr(text: String): RsInnerAttr =
        createFromText("#![$text]")
            ?: error("Failed to create an inner attribute from text: `$text`")

    fun createMatchBody(enumName: String?, variants: List<RsEnumVariant>): RsMatchBody {
        val matchBodyText = variants.joinToString(",\n", postfix = ",") { variant ->
            val tupleFields = variant.tupleFields?.tupleFieldDeclList
            val blockFields = variant.blockFields
            val suffix = when {
                tupleFields != null -> tupleFields.joinToString(", ", " (", ")") { "_" }
                blockFields != null -> " { .. }"
                else -> ""
            }
            val prefix = if (enumName != null) "$enumName::" else ""
            "$prefix${variant.name}$suffix => {}"
        }
        return createExpressionOfType<RsMatchExpr>("match x { $matchBodyText }").matchBody
            ?: error("Failed to create match body from text: `$matchBodyText`")
    }

    private inline fun <reified T : RsElement> createFromText(code: String): T? =
        createFile(code).descendantOfTypeStrict()

    fun createComma(): PsiElement =
        createFromText<RsValueParameter>("fn f(_ : (), )")!!.nextSibling

    fun createSemicolon(): PsiElement =
        createFromText<RsConstant>("const C: () = ();")!!.semicolon!!

    fun createColon(): PsiElement =
        createFromText<RsConstant>("const C: () = ();")!!.colon!!

    fun createEq(): PsiElement =
        createFromText<RsConstant>("const C: () = ();")!!.eq!!

    fun createIn(): PsiElement =
        createFromText<RsConstant>("pub(in self) const C: () = ();")?.vis?.visRestriction?.`in`
            ?: error("Failed to create `in` element")

    fun createNewline(): PsiElement = createWhitespace("\n")

    fun createWhitespace(ws: String): PsiElement =
        PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(ws)

    fun createUnsafeKeyword(): PsiElement =
        createFromText<RsFunction>("unsafe fn foo(){}")?.unsafe
            ?: error("Failed to create unsafe element")

    fun createFunction(
        text: String
    ): RsFunction =
        createFromText(text)
            ?: error("Failed to create function element: text")

    fun createImpl(name: String, functions: List<RsFunction>): RsImplItem =
        createFromText("impl $name {\n${functions.joinToString(separator = "\n", transform = { it.text })}\n}")
            ?: error("Failed to create RsImplItem element")

    fun createSimpleValueParameterList(name: String, type: RsTypeReference): RsValueParameterList {
        return createFromText<RsFunction>("fn main($name: ${type.text}){}")
            ?.valueParameterList
            ?: error("Failed to create parameter element")
    }

    fun createValueParameter(name: String, type: RsTypeReference, mutable: Boolean = false, lifetime: RsLifetime? = null): RsValueParameter {
        return createFromText<RsFunction>("fn main($name: &${if (lifetime != null) lifetime.text + " " else ""}${if (mutable) "mut " else ""}${type.text}){}")
            ?.valueParameterList?.valueParameterList?.get(0)
            ?: error("Failed to create parameter element")
    }

    fun tryCreatePat(text: CharSequence): RsPat? =
        createFromText("fn f($text: ()) {}")

    fun createPatBinding(name: String, mutable: Boolean = false, ref: Boolean = false): RsPatBinding =
        (createStatement("let ${"ref ".iff(ref)}${"mut ".iff(mutable)}$name = 10;") as RsLetDecl).pat
            ?.firstChild as RsPatBinding?
            ?: error("Failed to create pat element")

    fun createCastExpr(expr: RsExpr, typeText: String): RsCastExpr = when (expr) {
        is RsBinaryExpr -> createExpressionOfType("(${expr.text}) as $typeText")
        else -> createExpressionOfType("${expr.text} as $typeText")
    }

    fun createFunctionCall(functionName: String, arguments: Iterable<RsExpr>): RsCallExpr =
        createExpressionOfType("$functionName(${arguments.joinToString { it.text }})")

    fun createAssocFunctionCall(typeText: String, methodNameText: String, arguments: Iterable<RsExpr>): RsCallExpr =
        createExpressionOfType("$typeText::$methodNameText(${arguments.joinToString { it.text }})")

    fun createNoArgsMethodCall(expr: RsExpr, methodNameText: String): RsDotExpr = when (expr) {
        is RsBinaryExpr, is RsUnaryExpr, is RsCastExpr -> createExpressionOfType("(${expr.text}).$methodNameText()")
        else -> createExpressionOfType("${expr.text}.$methodNameText()")
    }

    fun createDerefExpr(expr: RsExpr, nOfDerefs: Int = 1): RsExpr =
        if (nOfDerefs > 0)
            when (expr) {
                is RsBinaryExpr, is RsCastExpr -> createExpressionOfType("${"*".repeat(nOfDerefs)}(${expr.text})")
                else -> createExpressionOfType("${"*".repeat(nOfDerefs)}${expr.text}")
            }
        else expr

    fun createRefExpr(expr: RsExpr, muts: List<Mutability> = listOf(IMMUTABLE)): RsExpr =
        if (!muts.none())
            when (expr) {
                is RsBinaryExpr, is RsCastExpr -> createExpressionOfType("${mutsToRefs(muts)}(${expr.text})")
                else -> createExpressionOfType("${mutsToRefs(muts)}${expr.text}")
            }
        else expr

    fun createVisRestriction(pathText: String): RsVisRestriction =
        createFromText<RsFunction>("pub(in $pathText) fn foo() {}")?.vis?.visRestriction
            ?: error("Failed to create vis restriction element")

    private inline fun <reified E : RsExpr> createExpressionOfType(text: String): E =
        createExpression(text) as? E
            ?: error("Failed to create ${E::class.simpleName} from `$text`")

    enum class PathNamespace {
        TYPES,
        VALUES
    }
}

private fun RsFunction.getSignatureText(subst: Substitution): String? {
    val unsafe = if (isUnsafe) "unsafe " else ""
    // We can't simply take a substring of original method declaration
    // because of anonymous parameters.
    val name = name ?: return null
    val generics = typeParameterList?.text ?: ""

    val selfArgument = listOfNotNull(selfParameter?.substAndGetText(subst))
    val valueArguments = valueParameters.map {
        // fix possible anon parameter
        "${it.pat?.text ?: "_"}: ${it.typeReference?.substAndGetText(subst) ?: "()"}"
    }
    val allArguments = selfArgument + valueArguments

    val ret = retType?.typeReference?.substAndGetText(subst)?.let { "-> $it " } ?: ""
    val where = whereClause?.text ?: ""
    return "${unsafe}fn $name$generics(${allArguments.joinToString(",")}) $ret$where"
}

private fun String.iff(cond: Boolean) = if (cond) this + " " else " "

private fun RsTypeReference.substAndGetText(subst: Substitution): String {
    val substitutedType = type.substitute(subst)
    val hasLifetime = refLikeType?.lifetime != null
    return if (hasLifetime) substitutedType.insertionSafeTextWithLifetimes else substitutedType.insertionSafeText
}

private fun RsSelfParameter.substAndGetText(subst: Substitution): String =
    buildString {
        append(and?.text ?: "")
        val region = lifetime.resolve().substitute(subst)
        if (region != ReUnknown) append("$region ")
        if (mutability == MUTABLE) append("mut ")
        append(self.text)
    }

private fun mutsToRefs(mutability: List<Mutability>): String =
    mutability.joinToString("", "", "") {
        when (it) {
            IMMUTABLE -> "&"
            MUTABLE -> "&mut "
        }
    }
