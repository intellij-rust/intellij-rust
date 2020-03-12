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
import org.rust.ide.inspections.checkMatch.Pattern
import org.rust.ide.presentation.insertionSafeTextWithAliasesAndLifetimes
import org.rust.ide.presentation.insertionSafeTextWithLifetimes
import org.rust.lang.RsFileType
import org.rust.lang.core.macros.MacroExpansionContext
import org.rust.lang.core.macros.prepareExpandedTextForParsing
import org.rust.lang.core.parser.RustParserUtil.PathParsingMode
import org.rust.lang.core.parser.RustParserUtil.PathParsingMode.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.VALUES
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.ty.Mutability.MUTABLE
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.type

class RsPsiFactory(
    private val project: Project,
    private val markGenerated: Boolean = true,
    private val eventSystemEnabled: Boolean = false
) {
    fun createFile(text: CharSequence): RsFile =
        PsiFileFactory.getInstance(project)
            .createFileFromText(
                "DUMMY.rs",
                RsFileType,
                text,
                /*modificationStamp =*/ LocalTimeCounter.currentTime(), // default value
                /*eventSystemEnabled =*/ eventSystemEnabled, // `false` by default
                /*markAsCopy =*/ markGenerated // `true` by default
            ) as RsFile

    fun createMacroBody(text: String): RsMacroBody? = createFromText(
        "macro_rules! m $text"
    )

    fun createMacroCall(
        context: MacroExpansionContext,
        braces: MacroBraces,
        macroName: String,
        vararg arguments: String
    ): RsMacroCall = createMacroCall(context, braces, macroName, arguments.joinToString(", "))

    fun createMacroCall(
        context: MacroExpansionContext,
        braces: MacroBraces,
        macroName: String,
        argument: String
    ): RsMacroCall {
        val appendSemicolon = (context == MacroExpansionContext.ITEM || context == MacroExpansionContext.STMT) &&
            braces.needsSemicolon
        val semicolon = if (appendSemicolon) ";" else ""
        return createFromText(context.prepareExpandedTextForParsing("$macroName!${braces.wrap(argument)}$semicolon"))
            ?: error("Failed to create macro call")
    }

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
        createFromText("fn main() { let _ = $text; }")

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

    fun createIfElseExpression(condition: RsExpr, thenBlock: RsBlock, elseBlock: RsBlock): RsIfExpr {
        val resultIfExpr = createExpressionOfType<RsIfExpr>("if ${condition.text} { () } else { () }")
        resultIfExpr.block!!.replace(thenBlock)
        resultIfExpr.elseBranch!!.block!!.replace(elseBlock)

        return resultIfExpr
    }

    fun createBlockExpr(body: CharSequence): RsBlockExpr =
        createExpressionOfType("{ $body }")

    fun createUnsafeBlockExpr(body: String): RsBlockExpr =
        createExpressionOfType("unsafe { $body }")

    fun createRetExpr(expr: String): RsRetExpr =
        createExpressionOfType("return $expr")

    fun tryCreatePath(text: String, ns: PathParsingMode = TYPE): RsPath? {
        val path = when (ns) {
            TYPE -> createFromText("fn foo(t: $text) {}")
            VALUE -> createFromText<RsPathExpr>("fn main() { $text; }")?.path
            NO_TYPE_ARGS -> error("$NO_TYPE_ARGS mode is not supported; use $TYPE")
        } ?: return null
        if (path.text != text) return null
        return path
    }

    fun createStructLiteral(name: String): RsStructLiteral =
        createExpressionOfType("$name { }")

    fun createStructLiteralField(name: String, value: String): RsStructLiteralField {
        return createExpressionOfType<RsStructLiteral>("S { $name: $value }")
            .structLiteralBody
            .structLiteralFieldList[0]
    }

    fun createStructLiteralField(name: String, value: RsExpr? = null): RsStructLiteralField {
        val structLiteralField = createExpressionOfType<RsStructLiteral>("S { $name: () }")
            .structLiteralBody
            .structLiteralFieldList[0]
        if (value != null) structLiteralField.expr?.replace(value)
        return structLiteralField
    }

    data class BlockField(val pub: Boolean, val name: String, val type: Ty)

    fun createBlockFields(fields: List<BlockField>): RsBlockFields {
        val fieldsText = fields.joinToString(separator = ",\n") {
            "${"pub".iff(it.pub)}${it.name}: ${it.type.insertionSafeTextWithLifetimes}"
        }
        return createStruct("struct S { $fieldsText }")
            .blockFields!!
    }

    fun createEnum(text: String): RsEnumItem =
        createFromText(text)
            ?: error("Failed to create enum from text: `$text`")

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

    fun createUseItem(text: String, visibility: String = ""): RsUseItem =
        createFromText("$visibility use $text;")
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

    fun createTraitMethodMember(text: String): RsFunction {
        return createFromText<RsFunction>("trait Foo { $text }")
            ?: error("Failed to create a method member from text: `$text`")
    }

    fun createMembers(text: String): RsMembers {
        return createFromText("impl T for S {$text}") ?: error("Failed to create members from text: `$text`")
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
            .map {
                //ignore default type parameter
                it.text.take(it.eq?.startOffsetInParent ?: it.textLength)
            }

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

    fun createMatchBody(context: RsElement, enumName: String, variants: List<RsEnumVariant>): RsMatchBody {
        val matchBodyText = variants.joinToString(",\n", postfix = ",") { variant ->
            val variantName = variant.name ?: return@joinToString ""
            val tupleFields = variant.tupleFields?.tupleFieldDeclList
            val blockFields = variant.blockFields
            val suffix = when {
                tupleFields != null -> tupleFields.joinToString(", ", " (", ")") { "_" }
                blockFields != null -> " { .. }"
                else -> ""
            }
            val prefix = if (context.findInScope(variantName, VALUES) != variant) "$enumName::" else ""
            "$prefix$variantName$suffix => {}"
        }
        return createExpressionOfType<RsMatchExpr>("match x { $matchBodyText }").matchBody
            ?: error("Failed to create match body from text: `$matchBodyText`")
    }

    fun createMatchBody(patterns: List<Pattern>, ctx: RsElement? = null): RsMatchBody {
        val arms = patterns.joinToString("\n") { "${it.text(ctx)} => {}" }
        return createExpressionOfType<RsMatchExpr>("match x { $arms }").matchBody
            ?: error("Failed to create match body from patterns: `$arms`")
    }

    private inline fun <reified T : RsElement> createFromText(code: CharSequence): T? =
        createFile(code).descendantOfTypeStrict()

    fun createPub(): RsVis =
        createFromText("pub fn f() {}")
            ?: error("Failed to create `pub` element")

    fun createPubCrateRestricted(): RsVis =
        createFromText("pub(crate) fn f() {}")
            ?: error("Failed to create `pub(crate)` element")

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

    fun createFunction(text: String): RsFunction =
        createFromText(text)
            ?: error("Failed to create function element: $text")

    fun createRetType(ty: String): RsRetType =
        createFromText("fn foo() -> $ty {}")
            ?: error("Failed to create function return type: $ty")

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

    fun createPatFieldFull(name: String, value: String): RsPatFieldFull =
        createFromText("fn f(A{$name: $value}: ()) {}")
            ?: error("Failed to create full field pattern")

    fun createPatBinding(name: String, mutable: Boolean = false, ref: Boolean = false): RsPatBinding =
        (createStatement("let ${"ref ".iff(ref)}${"mut ".iff(mutable)}$name = 10;") as RsLetDecl).pat
            ?.firstChild as RsPatBinding?
            ?: error("Failed to create pat element")

    fun createPatField(name: String): RsPatField =
        createFromText("""
            struct Foo { bar: i32 }
            fn baz(foo: Foo) {
                let Foo { $name } = foo;
            }
        """) ?: error("Failed to create pat field")

    fun createPatStruct(struct: RsStructItem): RsPatStruct {
        val structName = struct.name ?: error("Failed to create pat struct")
        val pad = if (struct.namedFields.isEmpty()) "" else " "
        val body = struct.namedFields
            .joinToString(separator = ", ", prefix = " {$pad", postfix = "$pad}") { it.name ?: "_" }
        return createFromText("fn f($structName$body: $structName) {}") ?: error("Failed to create pat struct")
    }

    fun createPatTupleStruct(struct: RsStructItem): RsPatTupleStruct {
        val structName = struct.name ?: error("Failed to create pat tuple struct")
        val body = struct.positionalFields
            .joinToString(separator = ", ", prefix = "(", postfix = ")") { "_${it.name}" }
        return createFromText("fn f($structName$body: $structName) {}") ?: error("Failed to create pat tuple struct")
    }

    fun createPatTuple(fieldNum: Int): RsPatTup {
        val tuple = (0 until fieldNum).joinToString(separator = ", ", prefix = "(", postfix = ")") { "_$it" }
        return createFromText("fn f() { let $tuple = x; }") ?: error("Failed to create pat tuple")
    }

    fun createCastExpr(expr: RsExpr, typeText: String): RsCastExpr = when (expr) {
        is RsBinaryExpr -> createExpressionOfType("(${expr.text}) as $typeText")
        else -> createExpressionOfType("${expr.text} as $typeText")
    }

    fun createFunctionCall(functionName: String, arguments: Iterable<RsExpr>): RsCallExpr =
        createExpressionOfType("$functionName(${arguments.joinToString { it.text }})")

    fun createAssocFunctionCall(typeText: String, methodNameText: String, arguments: Iterable<RsExpr>): RsCallExpr {
        val isCorrectTypePath = tryCreatePath(typeText) != null
        val typePath = if (isCorrectTypePath) typeText else "<$typeText>"
        return createExpressionOfType("$typePath::$methodNameText(${arguments.joinToString { it.text }})")
    }

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

    fun createDynTraitType(pathText: String): RsTraitType =
        createFromText("type T = &dyn $pathText;}")
            ?: error("Failed to create trait type")
}

private fun String.iff(cond: Boolean) = if (cond) this + " " else " "

fun RsTypeReference.substAndGetText(subst: Substitution): String =
    type.substitute(subst).insertionSafeTextWithAliasesAndLifetimes

private fun mutsToRefs(mutability: List<Mutability>): String =
    mutability.joinToString("", "", "") {
        when (it) {
            IMMUTABLE -> "&"
            MUTABLE -> "&mut "
        }
    }
