package org.rust.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.util.ancestors
import org.rust.lang.doc.documentationAsHtml

class RsDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        if (element !is RsDocAndAttributeOwner) return null

        val name = if (element is RsMod) element.modName else element.name
        val header = if (name != null) "<pre>$name</pre>\n" else ""
        val functionSignature = (element as? RsFunction)?.signatureText
        val signature = if (functionSignature != null) "<pre>$functionSignature</pre>\n" else ""
        val doc = element.documentationAsHtml() ?: ""
        return header + signature + doc
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? {
        val formatString = when (element) {
            is RsFunction -> element.signatureText
            is RsStructItem -> element.signatureText
            is RsFieldDecl -> element.signatureText
            is RsEnumItem -> element.signatureText
            is RsEnumVariant -> element.signatureText
            is RsTraitItem -> element.signatureText
            is RsTypeAlias -> element.signatureText
            is RsConstant -> element.signatureText
            is RsSelfParameter -> element.signatureText
            is RsTypeParameter -> element.signatureText
            is RsLifetimeDecl -> element.signatureText
            is RsLabelDecl -> element.signatureText()
            is RsPatBinding -> {
                val owner = element.owner
                when (owner) {
                    is RsLetDecl -> owner.signatureText(element.identifier)
                    is RsValueParameter -> owner.signatureText(element.identifier)
                    is RsMatchArm -> owner.signatureText(element.identifier)
                    is RsCondition -> owner.signatureText(element.identifier)
                    else -> createSignatureText(element, element.identifier)
                }
            }
            is RsFile -> element.signatureText()
            is RsNamedElement -> element.signatureText
            else -> null
        } ?: return null
        return "$formatString${element.locationString}"
    }

    private fun createSignatureText(decl: RsCompositeElement, id: PsiElement?, stopAt: List<PsiElement?> = emptyList()): String? {
        if (id == null) return null

        // Remove trailing spaces, comments and attributes
        val signatureStartElement = generateSequence(decl.firstChild) { it.nextSibling }
            .dropWhile { it is PsiWhiteSpace || it is PsiComment || it is RsOuterAttr }
            .firstOrNull()

        val signatureStart = signatureStartElement?.startOffsetInParent ?: return id.text
        val idStart = id.ancestors.takeWhile { it != decl }.sumBy { it.startOffsetInParent }

        // pick (in order) elements we should stop at
        // if they all fail, drop down to the end of the id element
        val signatureEnd = stopAt
            .filterNotNull().firstOrNull()
            ?.let { it.startOffsetInParent + it.textLength }
            ?: idStart + id.textLength

        val idEnd = idStart + id.textLength
        check(signatureStart <= idStart && idStart <= idEnd &&
            idEnd <= signatureEnd && signatureEnd <= decl.textLength)

        val beforeIdent = decl.text.substring(signatureStart, idStart).escaped
        val afterIdent = decl.text.substring(idEnd, signatureEnd)
            .replace("""\s+""".toRegex(), " ")
            .replace("( ", "(")
            .replace(" )", ")")
            .replace(" ,", ",")
            .trimEnd()
            .escaped

        return "$beforeIdent<b>${id.text}</b>$afterIdent"
    }

    private val RsNamedElement.signatureText: String?
        get() = createSignatureText(this, navigationElement)

    private val RsFunction.signatureText: String?
        get() = createSignatureText(this, identifier, listOf(whereClause, retType, valueParameterList))

    private val RsStructItem.signatureText: String?
        get() = createSignatureText(this, identifier, if (blockFields != null) listOf(whereClause) else listOf(whereClause, tupleFields))

    private val RsFieldDecl.signatureText: String?
        get() = createSignatureText(this, identifier, listOf(typeReference))

    private val RsEnumItem.signatureText: String?
        get() = createSignatureText(this, identifier, listOf(whereClause))

    private val RsEnumVariant.signatureText: String?
        get() = createSignatureText(this, identifier, listOf(tupleFields))

    private val RsTraitItem.signatureText: String?
        get() = createSignatureText(this, identifier, listOf(whereClause))

    private val RsTypeAlias.signatureText: String?
        get() = createSignatureText(this, identifier, listOf(typeReference, typeParamBounds, whereClause))

    private val RsConstant.signatureText: String?
        get() = createSignatureText(this, identifier, listOf(expr, typeReference))

    private val RsTypeParameter.signatureText: String?
        get() = "<i>type parameter:</i> " + createSignatureText(this, identifier)

    private val RsLifetimeDecl.signatureText: String?
        get() = "<i>lifetime:</i> " + createSignatureText(this, quoteIdentifier)

    private fun RsLabelDecl.signatureText(): String? {
        val p = parent
        return when (p) {
            is RsLoopExpr -> p.signatureText
            is RsForExpr -> p.signatureText
            is RsWhileExpr -> p.signatureText
            else -> createSignatureText(this, quoteIdentifier)
        }
    }

    private val RsLoopExpr.signatureText: String?
        get() = createSignatureText(this, labelDecl.quoteIdentifier, listOf(loop))

    private val RsForExpr.signatureText: String?
        get() = createSignatureText(this, labelDecl.quoteIdentifier, listOf(expr, `in`, `for`))

    private val RsWhileExpr.signatureText: String?
        get() = createSignatureText(this, labelDecl.quoteIdentifier, listOf(condition, `while`))

    private val RsSelfParameter.signatureText: String?
        get() = createSignatureText(this, self, listOf(typeReference))

    private fun RsLetDecl.signatureText(id: PsiElement): String? =
        createSignatureText(this, id, listOf(typeReference))

    private fun RsValueParameter.signatureText(id: PsiElement): String? =
        "<i>value parameter:</i> " + createSignatureText(this, id, listOf(typeReference))

    private fun RsMatchArm.signatureText(id: PsiElement): String? =
        "<i>match arm:</i> " + createSignatureText(this, id, listOf(matchPat))

    private fun RsCondition.signatureText(id: PsiElement): String? =
        createSignatureText(this, id, listOf(lastChild))

    private fun RsFile.signatureText(): String? {
        val mName = modName
        return if (isCrateRoot) "<i>crate</i>"
            else if (mName != null) "mod $mName"
            else "<i>file</i>"
    }

    private val RsPatBinding.owner: PsiElement?
        get() = ancestors
            .drop(1).dropWhile { it is RsPat || it is RsMatchPat || it is RsPatField }
            .filterIsInstance<RsCompositeElement>()
            .firstOrNull()
    private val PsiElement.locationString: String
        get() = containingFile?.let { " [${it.name}]" }.orEmpty()

    private val String.escaped: String get() = StringUtil.escapeXml(this)

}
