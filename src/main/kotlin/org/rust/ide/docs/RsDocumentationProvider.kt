/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.project.Project
import com.intellij.openapiext.Testmark
import com.intellij.openapiext.hitOnFalse
import com.intellij.psi.*
import org.rust.cargo.project.workspace.PackageOrigin.DEPENDENCY
import org.rust.cargo.project.workspace.PackageOrigin.STDLIB
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.ide.presentation.presentableQualifiedName
import org.rust.ide.presentation.presentationInfo
import org.rust.ide.presentation.render
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.core.types.type
import org.rust.lang.doc.RsDocRenderMode
import org.rust.lang.doc.documentationAsHtml
import org.rust.openapiext.escaped
import org.rust.stdext.joinToWithBuffer

@Suppress("UnstableApiUsage")
class RsDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val buffer = StringBuilder()
        when (element) {
            is RsTypeParameter -> definition(buffer) { generateDoc(element, it) }
            is RsDocAndAttributeOwner -> generateDoc(element, buffer)
            is RsPatBinding -> definition(buffer) { generateDoc(element, it) }
            is RsPath -> generateDoc(element, buffer)
            else -> return null
        }
        return if (buffer.isEmpty()) null else buffer.toString()
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? = buildString {
        when (element) {
            is RsPatBinding -> generateDoc(element, this)
            is RsTypeParameter -> generateDoc(element, this)
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

    override fun collectDocComments(file: PsiFile, sink: DocCommentConsumer) {
        if (file !is RsFile) return
        for (element in SyntaxTraverser.psiTraverser(file)) {
            if (element is RsDocCommentImpl) {
                sink.accept(element)
            }
        }
    }

    private fun generateDoc(element: RsDocAndAttributeOwner, buffer: StringBuilder) {
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
    }

    private fun generateDoc(element: RsPatBinding, buffer: StringBuilder) {
        val presentationInfo = element.presentationInfo ?: return
        val type = element.type.render(useAliasNames = true).escaped
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

    private fun generateDoc(element: RsPath, buffer: StringBuilder) {
        val primitive = TyPrimitive.fromPath(element) ?: return
        val primitiveDocs = element.project.findFileInStdCrate("primitive_docs.rs") ?: return

        val mod = primitiveDocs.childrenOfType<RsModItem>().find {
            it.queryAttributes.hasAttributeWithKeyValue("doc", "primitive", primitive.name)
        } ?: return

        definition(buffer) {
            it += STD
            it += "\n"
            it += "primitive type "
            it.b { it += primitive.name }
        }
        content(buffer) { it += mod.documentationAsHtml(element) }
    }

    override fun getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement): PsiElement? {
        if (context !is RsElement) return null
        val qualifiedName = RsQualifiedName.from(link)
        return if (qualifiedName == null) {
            RsCodeFragmentFactory(context.project)
                .createPath(link, context)
                ?.reference
                ?.resolve()
        } else {
            qualifiedName.findPsiElement(psiManager, context)
        }
    }

    override fun getUrlFor(element: PsiElement, originalElement: PsiElement?): List<String> {
        val (qualifiedName, origin) = if (element is RsPath) {
            (RsQualifiedName.from(element) ?: return emptyList()) to STDLIB
        } else {
            if (element !is RsDocAndAttributeOwner ||
                element !is RsQualifiedNamedElement ||
                !element.hasExternalDocumentation) return emptyList()
            val origin = element.containingCrate?.origin
            RsQualifiedName.from(element) to origin
        }

        val pagePrefix = when (origin) {
            STDLIB -> STD_DOC_HOST
            DEPENDENCY -> {
                val pkg = (element as? RsElement)?.containingCargoPackage ?: return emptyList()
                // Packages without source don't have documentation at docs.rs
                if (pkg.source == null) {
                    Testmarks.pkgWithoutSource.hit()
                    return emptyList()
                }
                "$DOCS_RS_HOST/${pkg.name}/${pkg.version}"
            }
            else -> {
                Testmarks.nonDependency.hit()
                return emptyList()
            }
        }

        val pagePath = qualifiedName?.toUrlPath() ?: return emptyList()
        return listOf("$pagePrefix/$pagePath")
    }

    @Suppress("UnstableApiUsage")
    override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
        return (comment as? RsDocCommentImpl)?.documentationAsHtml(renderMode = RsDocRenderMode.INLINE_DOC_COMMENT)
    }

    private val RsDocAndAttributeOwner.hasExternalDocumentation: Boolean get() {
        // items with #[doc(hidden)] attribute don't have external documentation
        if (queryAttributes.isDocHidden) {
            Testmarks.docHidden.hit()
            return false
        }

        // private items don't have external documentation
        if (this is RsVisible) {
            if (this is RsAbstractable) {
                when (val owner = owner) {
                    is RsAbstractableOwner.Trait -> return owner.trait.hasExternalDocumentation
                    is RsAbstractableOwner.Impl -> {
                        return if (owner.isInherent)  {
                            visibility == RsVisibility.Public
                        } else {
                            owner.impl.traitRef?.resolveToTrait()?.hasExternalDocumentation == true
                        }
                    }
                }
            } else {
                if (visibility != RsVisibility.Public) return false
            }
        }

        // macros without #[macro_export] are not public and don't have external documentation
        if (this is RsMacro) {
            return Testmarks.notExportedMacro.hitOnFalse(hasMacroExport)
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
        const val DOCS_RS_HOST = "https://docs.rs"
    }

    object Testmarks {
        val docHidden = Testmark("docHidden")
        val notExportedMacro = Testmark("notExportedMacro")
        val pkgWithoutSource = Testmark("pkgWithoutSource")
        val nonDependency = Testmark("nonDependency")
    }
}

private fun RsDocAndAttributeOwner.header(buffer: StringBuilder) {
    val rawLines = when (this) {
        is RsNamedFieldDecl -> listOfNotNull((parent?.parent as? RsDocAndAttributeOwner)?.presentableQualifiedName)
        is RsStructOrEnumItemElement,
        is RsTraitItem,
        is RsMacro -> listOfNotNull(presentableQualifiedModName)
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
            declarationModifiers.joinTo(buffer, " ")
            buffer += " "
            buffer.b { it += name }
            typeParameterList?.generateDocumentation(buffer)
            valueParameterList?.generateDocumentation(buffer)
            retType?.generateDocumentation(buffer)
            listOf(buffer.toString()) + whereClause?.documentationText.orEmpty()
        }
        is RsConstant -> {
            val buffer = StringBuilder()
            declarationModifiers.joinTo(buffer, " ", "", " ")
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
                (this as RsItemElement).declarationModifiers.joinTo(buffer, " ", "", " ")
                buffer.b { it += name }
                (this as RsGenericDeclaration).typeParameterList?.generateDocumentation(buffer)
                (this as? RsTypeAlias)?.typeReference?.generateDocumentation(buffer, " = ")
                listOf(buffer.toString()) + whereClause?.documentationText.orEmpty()
            } else emptyList()
        }
        is RsMacro -> listOf("macro <b>$name</b>")
        is RsImplItem -> declarationText
        else -> emptyList()
    }
    rawLines.joinTo(builder, "<br>")
}

private val RsImplItem.declarationText: List<String> get() {
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
    return listOf(buffer.toString()) + whereClause?.documentationText.orEmpty()
}

private val RsTraitItem.declarationText: List<String> get() {
    val name = presentableQualifiedName ?: return emptyList()
    val buffer = StringBuilder(name)
    typeParameterList?.generateDocumentation(buffer)
    return listOf(buffer.toString()) + whereClause?.documentationText.orEmpty()
}

private val RsItemElement.declarationModifiers: List<String> get() {
    val modifiers = mutableListOf<String>()
    if (isPublic) {
        modifiers += "pub"
    }
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
            if (isExtern) {
                modifiers += "extern"
                abiName?.let { modifiers += it }
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
        else -> error("unexpected type $javaClass")
    }
    return modifiers
}

private val RsWhereClause.documentationText: List<String> get() {
    return listOf("where") + wherePredList.mapNotNull {
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

private val RsDocAndAttributeOwner.presentableQualifiedModName: String? get() =
    presentableQualifiedName?.removeSuffix("::$name")

private fun PsiElement.generateDocumentation(buffer: StringBuilder, prefix: String = "", suffix: String = "") {
    buffer += prefix
    when (this) {
        is RsPath -> generatePathDocumentation(this, buffer)
        is RsAssocTypeBinding -> {
            buffer += identifier.text
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
        is RsTypeArgumentList -> (lifetimeList + typeReferenceList + assocTypeBindingList)
            .joinToWithBuffer(buffer, ", ", "&lt;", "&gt;") { generateDocumentation(it) }
        is RsTypeParameterList -> (lifetimeParameterList + typeParameterList)
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
        is RsBaseType -> when (val kind = typeElement.kind) {
            RsBaseTypeKind.Unit -> buffer += "()"
            RsBaseTypeKind.Never -> buffer += "!"
            RsBaseTypeKind.Underscore -> buffer += "_"
            is RsBaseTypeKind.Path -> {
                val path = kind.path
                if (path.hasCself) {
                    buffer += "Self"
                } else {
                    path.generateDocumentation(buffer)
                }
            }
        }
        is RsTupleType -> typeElement.typeReferenceList.joinToWithBuffer(buffer, ", ", "(", ")") { generateDocumentation(it) }
        is RsArrayType -> {
            buffer += "["
            typeElement.typeReference?.generateDocumentation(buffer)
            if (!typeElement.isSlice) {
                buffer += "; "
                buffer.append(typeElement.arraySize ?: "<unknown>")
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
            typeElement.valueParameterList.generateDocumentation(buffer)
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
