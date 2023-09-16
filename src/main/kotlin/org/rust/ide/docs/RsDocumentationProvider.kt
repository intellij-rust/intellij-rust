/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import org.jetbrains.annotations.TestOnly
import org.rust.RsBundle
import org.rust.cargo.project.workspace.PackageOrigin.*
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.ide.actions.macroExpansion.expandMacroForView
import org.rust.ide.actions.macroExpansion.reformatMacroExpansion
import org.rust.ide.presentation.presentableQualifiedName
import org.rust.ide.presentation.presentationInfo
import org.rust.ide.presentation.render
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.macros.findExpansionElementOrSelf
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.resolveTypeAliasToImpl
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.core.types.type
import org.rust.lang.doc.RsDocRenderMode
import org.rust.lang.doc.documentationAsHtml
import org.rust.lang.doc.psi.RsDocComment
import org.rust.openapiext.Testmark
import org.rust.openapiext.escaped
import org.rust.openapiext.hitOnFalse
import org.rust.stdext.RsResult
import org.rust.stdext.joinToWithBuffer
import java.util.function.Consumer

@Suppress("UnstableApiUsage")
class RsDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        @NlsSafe val buffer = StringBuilder()
        when (element) {
            is RsTypeParameter -> definition(buffer) { generateDoc(element, it) }
            is RsConstParameter -> definition(buffer) { generateDoc(element, it) }
            is RsDocAndAttributeOwner -> generateDoc(element, buffer, originalElement)
            is RsPatBinding -> definition(buffer) { generateDoc(element, it) }
            is RsPath -> generateDoc(element, buffer)
            else -> generateCustomDoc(element, buffer)
        }
        return if (buffer.isEmpty()) null else buffer.toString()
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? {
        @NlsSafe val string = buildString {
            when (element) {
                is RsPatBinding -> generateDoc(element, this)
                is RsTypeParameter -> generateDoc(element, this)
                is RsConstParameter -> generateDoc(element, this)
                is RsConstant -> this += element.presentationInfo?.quickDocumentationText
                is RsMod -> this += element.presentationInfo?.quickDocumentationText
                is RsItemElement,
                is RsMacro -> {
                    (element as RsDocAndAttributeOwner).header(this)
                    element.signature(this)
                }

                is RsNamedElement -> this += element.presentationInfo?.quickDocumentationText
                else -> return null
            }
        }
        return string
    }

    override fun collectDocComments(file: PsiFile, sink: Consumer<in PsiDocCommentBase>) {
        if (file !is RsFile) return
        for (element in SyntaxTraverser.psiTraverser(file)) {
            if (element is RsDocComment) {
                sink.accept(element)
            }
        }
    }

    private fun generateDoc(element: RsDocAndAttributeOwner, buffer: StringBuilder, originalElement: PsiElement?) {
        definition(buffer) {
            element.header(it)
            element.signature(it)
        }
        var text = element.documentationAsHtml()
        if (text.isNullOrEmpty() && element is RsAbstractable && element.owner.isTraitImpl) {
            // Show documentation of the corresponding trait item if own documentation is empty
            val superElement = element.superItem as? RsDocAndAttributeOwner ?: return
            text = superElement.documentationAsHtml()
        }
        if (text.isNullOrEmpty()) return
        buffer += "\n" // Just for more pretty html text representation
        content(buffer) { it += text }
        buffer += "\n" // Just for more pretty html text representation
        val possiblyExpandedElement = originalElement?.findExpansionElementOrSelf()
        val macroCall = possiblyExpandedElement?.contextMacroCall
        if (macroCall != null) {
            generateExpansion(macroCall, buffer)
        }
    }

    private fun generateExpansion(call: RsPossibleMacroCall, buffer: StringBuilder) {
        val macroPreview = expandMacroForView(
            macroToExpand = call,
            expandRecursively = true,
            sizeLimit = macroExpansionLimitInBytes,
            hasWriteAccess = false,
        )
        content(buffer) { builder ->
            builder.h2 { em { append(RsBundle.message("quick.doc.macro.expansion")) } }
            when (macroPreview) {
                is RsResult.Ok -> builder.p {
                    val details = macroPreview.ok
                    val expanded = reformatMacroExpansion(details.macroToExpand, details.expansion, false)
                        .elements
                        .joinToString(separator = "\n") { it.text }
                    HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                        builder,
                        call.project,
                        call.language,
                        expanded,
                        DocumentationSettings.getHighlightingSaturation(false),
                    )
                }
                is RsResult.Err -> builder.p("grayed") {
                    em {
                        append(RsBundle.message("quick.doc.expansion.is.unavailable"))
                    }
                }
            }
        }
    }

    private fun generateDoc(element: RsPatBinding, buffer: StringBuilder) {
        val presentationInfo = element.presentationInfo ?: return
        val type = element.type.render().escaped
        buffer += presentationInfo.type
        buffer += " "
        buffer.b { it += presentationInfo.name }
        buffer += ": "
        buffer += type
    }

    private fun generateDoc(element: RsTypeParameter, buffer: StringBuilder) {
        val name = element.name ?: return
        buffer += "type parameter "
        buffer.b { it += name }
        val typeBounds = element.bounds
        if (typeBounds.isNotEmpty()) {
            typeBounds.joinToWithBuffer(buffer, " + ", ": ") { generateDocumentation(it) }
        }
        element.typeReference?.generateDocumentation(buffer, " = ")
    }

    private fun generateDoc(element: RsConstParameter, buffer: StringBuilder) {
        val name = element.name ?: return
        buffer += "const parameter "
        buffer.b { it += name }
        element.typeReference?.generateDocumentation(buffer, ": ")
        element.expr?.generateDocumentation(buffer, " = ")
    }

    private fun generateDoc(element: RsPath, buffer: StringBuilder) {
        val primitive = TyPrimitive.fromPath(element) ?: return
        val primitiveDocs = element.project.findFileInStdCrate("primitive_docs.rs") ?: return

        val mod = primitiveDocs.childrenOfType<RsModItem>().find {
            it.queryAttributes.hasAttributeWithValue("rustc_doc_primitive", primitive.name) ||
            // BACKCOMPAT: Rust 1.71.
            // Since Rust 1.71 `#[doc(primitive = "primitive_name")]` is replace with `#[rustc_doc_primitive = "primitive_name"]`
            // https://github.com/rust-lang/rust/commit/364e961417c4308f8a1d3b7ec69ead9d98af2a01
            it.queryAttributes.hasAttributeWithKeyValue("doc", "primitive", primitive.name)
        } ?: return

        definition(buffer) { builder ->
            builder += STD
            builder += "\n"
            builder += "primitive type "
            builder.b { it += primitive.name }
        }
        content(buffer) { it += mod.documentationAsHtml(element) }
    }

    private fun generateCustomDoc(element: PsiElement, buffer: StringBuilder) {
        if (element.isKeywordLike()) {
            val keywordDocs = element.project.findFileInStdCrate("keyword_docs.rs") ?: return
            val keywordName = element.text
            val mod = keywordDocs.childrenOfType<RsModItem>().find {
                it.queryAttributes.hasAttributeWithKeyValue("doc", "keyword", keywordName)
            } ?: return

            definition(buffer) { builder ->
                builder += STD
                builder += "\n"
                builder += "keyword "
                builder.b { it += keywordName }
            }
            content(buffer) { it += mod.documentationAsHtml(element) }
        }
    }

    override fun getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement): PsiElement? {
        val element = context as? RsElement ?: context.parent as? RsElement ?: return null
        val qualifiedName = RsQualifiedName.from(link)
        return if (qualifiedName == null) {
            RsCodeFragmentFactory(context.project)
                .createPath(link, element)
                ?.reference
                ?.resolveTypeAliasToImpl()
        } else {
            qualifiedName.findPsiElement(psiManager, element)
        }
    }

    override fun getUrlFor(element: PsiElement, originalElement: PsiElement?): List<String> {
        val (qualifiedName, origin) = when {
            element is RsDocAndAttributeOwner && element is RsQualifiedNamedElement && element.hasExternalDocumentation -> {
                val origin = element.containingCrate.origin
                RsQualifiedName.from(element) to origin
            }
            else -> {
                val qualifiedName = RsQualifiedName.from(element) ?: return emptyList()
                qualifiedName to STDLIB
            }
        }

        val baseUrl = getExternalDocumentationBaseUrl()
        val pagePrefix = when (origin) {
            STDLIB -> STD_DOC_HOST
            DEPENDENCY, STDLIB_DEPENDENCY -> {
                val pkg = (element as? RsElement)?.containingCargoPackage ?: return emptyList()
                // Packages without source don't have documentation at docs.rs
                if (pkg.source == null) {
                    Testmarks.PkgWithoutSource.hit()
                    return emptyList()
                }
                "$baseUrl${pkg.name}/${pkg.version}"
            }
            else -> {
                Testmarks.NonDependency.hit()
                return emptyList()
            }
        }

        val pagePath = qualifiedName?.toUrlPath() ?: return emptyList()
        return listOf("$pagePrefix/$pagePath")
    }

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        // Don't show documentation for keywords like `self`, `super`, etc. when they are part of path.
        // We want to show documentation for the corresponding item that path references to
        if (contextElement?.isKeywordLike() == true && contextElement.parent !is RsPath) return contextElement
        return null
    }

    @Suppress("UnstableApiUsage")
    override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
        return (comment as? RsDocComment)?.documentationAsHtml(renderMode = RsDocRenderMode.INLINE_DOC_COMMENT)
    }

    private val RsDocAndAttributeOwner.hasExternalDocumentation: Boolean
        get() {
            // items with #[doc(hidden)] attribute don't have external documentation
            if (queryAttributes.isDocHidden) {
                Testmarks.DocHidden.hit()
                return false
            }

            // private items don't have external documentation
            if (this is RsVisible) {
                if (this is RsAbstractable) {
                    when (val owner = owner) {
                        is RsAbstractableOwner.Trait -> return owner.trait.hasExternalDocumentation
                        is RsAbstractableOwner.Impl -> {
                            return if (owner.isInherent) {
                                visibility == RsVisibility.Public
                            } else {
                                owner.impl.traitRef?.resolveToTrait()?.hasExternalDocumentation == true
                            }
                        }
                        else -> Unit
                    }
                } else {
                    if (visibility != RsVisibility.Public) return false
                }
            }

            // macros without #[macro_export] are not public and don't have external documentation
            if (this is RsMacro) {
                return Testmarks.NotExportedMacro.hitOnFalse(hasMacroExport)
            }
            // TODO: we should take into account real path of item for user, i.e. take into account reexports
            // instead of already resolved item path
            return containingMod.superMods.all { it.isPublic }
        }

    private fun Project.findFileInStdCrate(name: String): RsFile? {
        return crateGraph.topSortedCrates
            .find { it.origin == STDLIB && it.normName == STD }
            ?.rootMod
            ?.parent
            ?.findFile(name)
            ?.rustFile
    }

    companion object {
        const val STD_DOC_HOST = "https://doc.rust-lang.org"
        const val macroExpansionLimitInBytes: Int = 4096
    }

    object Testmarks {
        object DocHidden : Testmark()
        object NotExportedMacro : Testmark()
        object PkgWithoutSource : Testmark()
        object NonDependency : Testmark()
    }
}

const val EXTERNAL_DOCUMENTATION_URL_SETTING_KEY: String = "org.rust.external.doc.url"

/**
 * Returns the base URL used for creating external code and crate documentation links.
 */
fun getExternalDocumentationBaseUrl(): String {
    val url = AdvancedSettings.getString(EXTERNAL_DOCUMENTATION_URL_SETTING_KEY)
    return if (url.endsWith("/")) {
        url
    } else {
        "$url/"
    }
}

@TestOnly
fun withExternalDocumentationBaseUrl(url: String, action: () -> Unit) {
    val originalUrl = getExternalDocumentationBaseUrl()
    try {
        AdvancedSettings.setString(EXTERNAL_DOCUMENTATION_URL_SETTING_KEY, url)
        action()
    } finally {
        AdvancedSettings.setString(EXTERNAL_DOCUMENTATION_URL_SETTING_KEY, originalUrl)
    }
}


private fun RsDocAndAttributeOwner.header(buffer: StringBuilder) {
    val rawLines = when (this) {
        is RsNamedFieldDecl -> listOfNotNull((parent?.parent as? RsDocAndAttributeOwner)?.presentableQualifiedName)
        is RsStructOrEnumItemElement,
        is RsTraitItem,
        is RsMacroDefinitionBase -> listOfNotNull(presentableQualifiedModName)
        is RsAbstractable -> when (val owner = owner) {
            is RsAbstractableOwner.Foreign,
            is RsAbstractableOwner.Free -> listOfNotNull(presentableQualifiedModName)
            is RsAbstractableOwner.Impl -> listOfNotNull(presentableQualifiedModName) + owner.impl.declarationText
            is RsAbstractableOwner.Trait -> owner.trait.declarationText
        }
        else -> listOfNotNull(presentableQualifiedName)
    }
    rawLines.joinTo(buffer, "<br>")
    if (rawLines.isNotEmpty()) {
        buffer += "\n"
    }
}

fun RsDocAndAttributeOwner.signature(builder: StringBuilder) {
    val rawLines = when (this) {
        is RsNamedFieldDecl -> listOfNotNull(presentationInfo?.signatureText)
        is RsFunction -> {
            val buffer = StringBuilder()
            declarationModifiers.joinTo(buffer, separator = " ", postfix = " ")
            buffer.b { it += name }
            typeParameterList?.generateDocumentation(buffer)
            valueParameterList?.generateDocumentation(buffer)
            retType?.generateDocumentation(buffer)
            listOf(buffer.toString()) + wherePreds.documentationText
        }
        is RsConstant -> {
            val buffer = StringBuilder()
            declarationModifiers.joinTo(buffer, separator = " ", postfix = " ")
            buffer.b { it += name }
            typeReference?.generateDocumentation(buffer, ": ")
            expr?.generateDocumentation(buffer, " = ")
            listOf(buffer.toString())
        }
        // All these types extend RsItemElement and RsGenericDeclaration interfaces so all casts are safe
        is RsStructOrEnumItemElement, is RsTraitItem, is RsTypeAlias -> {
            val name = name
            if (name != null) {
                val buffer = StringBuilder()
                (this as RsItemElement).declarationModifiers.joinTo(buffer, separator = " ", postfix = " ")
                buffer.b { it += name }
                (this as RsGenericDeclaration).typeParameterList?.generateDocumentation(buffer)
                (this as? RsTypeAlias)?.typeReference?.generateDocumentation(buffer, " = ")
                listOf(buffer.toString()) + wherePreds.documentationText
            } else emptyList()
        }
        is RsMacro -> listOf("macro <b>$name</b>")
        is RsMacro2 -> {
            val buffer = StringBuilder()
            declarationModifiers.joinTo(buffer, separator = " ", postfix = " ")
            buffer.b { it += name }
            listOf(buffer.toString())
        }
        is RsImplItem -> declarationText
        else -> emptyList()
    }
    rawLines.joinTo(builder, "<br>")
}

private val RsImplItem.declarationText: List<String>
    get() {
        val typeRef = typeReference ?: return emptyList()

        val buffer = StringBuilder("impl")
        typeParameterList?.generateDocumentation(buffer)
        buffer += " "
        val traitRef = traitRef
        if (traitRef != null) {
            traitRef.generateDocumentation(buffer)
            buffer += " for "
        }
        typeRef.generateDocumentation(buffer)
        return listOf(buffer.toString()) + wherePreds.documentationText
    }

private val RsTraitItem.declarationText: List<String>
    get() {
        val name = presentableQualifiedName ?: return emptyList()
        val buffer = StringBuilder(name)
        typeParameterList?.generateDocumentation(buffer)
        return listOf(buffer.toString()) + wherePreds.documentationText
    }

private val RsItemElement.declarationModifiers: List<String>
    get() {
        val modifiers = mutableListOf<String>()
        vis?.text?.let { modifiers += it }
        when (this) {
            is RsFunction -> {
                if (isAsync) {
                    modifiers += "async"
                }
                if (isConst) {
                    modifiers += "const"
                }
                if (isUnsafe) {
                    modifiers += "unsafe"
                }
                if (isActuallyExtern) {
                    modifiers += "extern"
                    literalAbiName?.let { modifiers += "\"$it\"" }
                }
                modifiers += "fn"
            }
            is RsStructItem -> modifiers += "struct"
            is RsEnumItem -> modifiers += "enum"
            is RsConstant -> modifiers += if (isConst) "const" else "static"
            is RsTypeAlias -> modifiers += "type"
            is RsTraitItem -> {
                if (isUnsafe) {
                    modifiers += "unsafe"
                }
                modifiers += "trait"
            }
            is RsMacro2 -> modifiers += "macro"
            else -> error("unexpected type $javaClass")
        }
        return modifiers
    }

private val List<RsWherePred>.documentationText: List<String>
    get() {
        if (isEmpty()) return emptyList()
        return listOf("where") + this.mapNotNull {
            val buffer = StringBuilder()
            val lifetime = it.lifetime
            val typeReference = it.typeReference

            when {
                lifetime != null -> {
                    lifetime.generateDocumentation(buffer)
                    it.lifetimeParamBounds?.generateDocumentation(buffer)
                }
                typeReference != null -> {
                    typeReference.generateDocumentation(buffer)
                    it.typeParamBounds?.generateDocumentation(buffer)
                }
                else -> return@mapNotNull null
            }
            "&nbsp;&nbsp;&nbsp;&nbsp;$buffer,"
        }
    }

private val RsDocAndAttributeOwner.presentableQualifiedModName: String?
    get() = presentableQualifiedName?.removeSuffix("::$name")

private fun PsiElement.generateDocumentation(buffer: StringBuilder, prefix: String = "", suffix: String = "") {
    buffer += prefix
    when (this) {
        is RsPath -> generatePathDocumentation(this, buffer)
        is RsAssocTypeBinding -> {
            path.generateDocumentation(buffer)
            typeReference?.generateDocumentation(buffer, " = ")
        }
        is RsTraitRef -> path.generateDocumentation(buffer)
        is RsLifetimeParamBounds -> lifetimeList.joinToWithBuffer(buffer, " + ", ": ") { generateDocumentation(it) }
        is RsTypeParamBounds -> {
            if (polyboundList.isNotEmpty()) {
                polyboundList.joinToWithBuffer(buffer, " + ", ": ") { generateDocumentation(it) }
            }
        }
        // TODO: support 'for lifetimes'
        is RsPolybound -> {
            if (q != null) {
                buffer += "?"
            }
            (bound.lifetime ?: bound.traitRef)?.generateDocumentation(buffer)
        }
        is RsTypeArgumentList -> (lifetimeList + typeReferenceList + exprList + assocTypeBindingList)
            .sortedBy { it.startOffset }
            .joinToWithBuffer(buffer, ", ", "&lt;", "&gt;") { generateDocumentation(it) }
        is RsTypeParameterList -> getGenericParameters()
            .joinToWithBuffer(buffer, ", ", "&lt;", "&gt;") { generateDocumentation(it) }
        is RsValueParameterList -> (listOfNotNull(selfParameter) + valueParameterList + listOfNotNull(variadic))
            .joinToWithBuffer(buffer, ", ", "(", ")") { generateDocumentation(it) }
        is RsLifetimeParameter -> {
            buffer += quoteIdentifier.text.escaped
            lifetimeParamBounds?.generateDocumentation(buffer)
        }
        is RsTypeParameter -> {
            buffer += name
            typeParamBounds?.generateDocumentation(buffer)
            typeReference?.generateDocumentation(buffer, " = ")
        }
        is RsConstParameter -> {
            buffer += "const "
            buffer += name
            typeReference?.generateDocumentation(buffer, ": ")
        }
        is RsValueParameter -> {
            pat?.generateDocumentation(buffer, suffix = ": ")
            typeReference?.generateDocumentation(buffer)
        }
        is RsVariadic -> {
            pat?.generateDocumentation(buffer, suffix = ": ")
            buffer += dotdotdot.text
        }
        is RsTypeReference -> generateTypeReferenceDocumentation(this, buffer)
        is RsRetType -> typeReference?.generateDocumentation(buffer, " -&gt; ")
        is RsTypeQual -> {
            buffer += "&lt;"
            typeReference.generateDocumentation(buffer)
            traitRef?.generateDocumentation(buffer, " as ")
            buffer += "&gt;::"
        }
        else -> buffer += text.escaped
    }
    buffer += suffix
}

private fun generatePathDocumentation(element: RsPath, buffer: StringBuilder) {
    val path = element.path
    if (path != null) {
        buffer += path.text.escaped
        buffer += "::"
    }
    element.typeQual?.generateDocumentation(buffer)

    val name = element.referenceName.orEmpty()
    if (element.isLinkNeeded()) {
        createLink(buffer, element.link(), name)
    } else {
        buffer += name
    }

    val typeArgumentList = element.typeArgumentList
    val valueParameterList = element.valueParameterList
    when {
        typeArgumentList != null -> typeArgumentList.generateDocumentation(buffer)
        valueParameterList != null -> {
            valueParameterList.generateDocumentation(buffer)
            element.retType?.generateDocumentation(buffer)
        }
    }
}

private fun generateTypeReferenceDocumentation(element: RsTypeReference, buffer: StringBuilder) {
    when (val typeElement = element.skipParens()) {
        is RsUnitType -> buffer += "()"
        is RsNeverType -> buffer += "!"
        is RsInferType -> buffer += "_"
        is RsPathType -> {
            val path = typeElement.path
            if (path.hasCself) {
                buffer += "Self"
            } else {
                path.generateDocumentation(buffer)
            }
        }
        is RsTupleType -> typeElement.typeReferenceList.joinToWithBuffer(buffer, ", ", "(", ")") { generateDocumentation(it) }
        is RsArrayType -> {
            buffer += "["
            typeElement.typeReference?.generateDocumentation(buffer)
            if (!typeElement.isSlice) {
                buffer += "; "
                // Try to render raw text of expression when array size cannot be inferred.
                // It may be useful when array size is defined with const generic
                buffer.append(typeElement.arraySize ?: typeElement.expr?.text?.escaped ?: "<unknown>")
            }
            buffer += "]"
        }
        is RsRefLikeType -> {
            if (typeElement.isRef) {
                buffer += "&amp;"
                typeElement.lifetime?.generateDocumentation(buffer, suffix = " ")
                if (typeElement.mutability.isMut) {
                    buffer += "mut "
                }
            } else {
                buffer += "*"
                buffer += if (typeElement.mutability.isMut) "mut " else "const "
            }
            typeElement.typeReference?.generateDocumentation(buffer)
        }
        is RsFnPointerType -> {
            // TODO: handle abi
            buffer += "fn"
            typeElement.valueParameterList?.generateDocumentation(buffer)
            typeElement.retType?.generateDocumentation(buffer)
        }
        else -> buffer += element.text.escaped
    }
}

private fun RsPath.isLinkNeeded(): Boolean {
    val element = reference?.resolve()
    // If it'll find out that links for type parameters are useful
    // just check element for null
    return !(element == null || element is RsTypeParameter)
}

// TODO: use RsQualifiedName scheme
private fun RsPath.link(): String {
    val path = path
    val prefix = if (path != null) "${path.text.escaped}::" else typeQual?.text?.escaped
    return if (prefix != null) "$prefix${referenceName.orEmpty()}" else referenceName.orEmpty()
}

private fun createLink(buffer: StringBuilder, refText: String, text: String) {
    DocumentationManagerUtil.createHyperlink(buffer, refText, text, true)
}

private inline fun definition(buffer: StringBuilder, block: (StringBuilder) -> Unit) {
    buffer += DocumentationMarkup.DEFINITION_START
    block(buffer)
    buffer += DocumentationMarkup.DEFINITION_END
}

private inline fun content(buffer: StringBuilder, block: (StringBuilder) -> Unit) {
    buffer += DocumentationMarkup.CONTENT_START
    block(buffer)
    buffer += DocumentationMarkup.CONTENT_END
}

private operator fun StringBuilder.plusAssign(value: String?) {
    if (value != null) {
        append(value)
    }
}

private inline fun StringBuilder.b(action: (StringBuilder) -> Unit) {
    append("<b>")
    action(this)
    append("</b>")
}

private inline fun StringBuilder.h2(action: StringBuilder.() -> Unit) {
    append("<h2>")
    action(this)
    append("</h2>")
}

private inline fun StringBuilder.em(action: StringBuilder.() -> Unit) {
    append("<em>")
    action(this)
    append("</em>")
}

private inline fun StringBuilder.p(clazz: String? = null, action: StringBuilder.() -> Unit) {
    append("<p")
    if (clazz != null) {
        append(" class='$clazz'")
    }
    append(">")
    action(this)
    append("</p>")
}
