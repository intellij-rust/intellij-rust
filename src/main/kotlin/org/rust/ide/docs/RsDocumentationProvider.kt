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

    override fun getQuickNavigateInfo(e: PsiElement, originalElement: PsiElement?): String? {
        val formatString = when (e) {
            is RsFunction -> e.signatureText
            is RsStructItem -> createSignatureText(e, e.identifier, if (e.blockFields != null) listOf(e.whereClause) else listOf(e.whereClause, e.tupleFields))
            is RsFieldDecl -> createSignatureText(e, e.identifier, listOf(e.typeReference))
            is RsEnumItem -> createSignatureText(e, e.identifier, listOf(e.whereClause))
            is RsEnumVariant -> createSignatureText(e, e.identifier, listOf(e.tupleFields))
            is RsTraitItem -> createSignatureText(e, e.identifier, listOf(e.whereClause))
            is RsTypeAlias -> createSignatureText(e, e.identifier, listOf(e.typeReference, e.typeParamBounds, e.whereClause))
            is RsConstant -> createSignatureText(e, e.identifier, listOf(e.expr, e.typeReference))
            is RsSelfParameter -> createSignatureText(e, e.self, listOf(e.typeReference))
            is RsTypeParameter -> "<i>type parameter:</i> " + createSignatureText(e, e.identifier)
            is RsLifetimeDecl -> "<i>lifetime:</i> " + createSignatureText(e, e.quoteIdentifier)
            is RsLabelDecl -> {
                val p = e.parent
                when (p) {
                    is RsLoopExpr -> createSignatureText(p, p.labelDecl.quoteIdentifier, listOf(p.loop))
                    is RsForExpr -> createSignatureText(p, p.labelDecl.quoteIdentifier, listOf(p.expr, p.`in`, p.`for`))
                    is RsWhileExpr -> createSignatureText(p, p.labelDecl.quoteIdentifier, listOf(p.condition, p.`while`))
                    else -> createSignatureText(e, e.quoteIdentifier)
                }
            }
            is RsPatBinding -> {
                val owner = e.owner
                when (owner) {
                    is RsLetDecl -> createSignatureText(owner, e.identifier, listOf(owner.typeReference))
                    is RsValueParameter -> "<i>value parameter:</i> " + createSignatureText(owner, e.identifier, listOf(owner.typeReference))
                    is RsMatchArm -> "<i>match arm:</i> " + createSignatureText(owner, e.identifier, listOf(owner.matchPat))
                    is RsCondition -> createSignatureText(owner, e.identifier, listOf(owner.lastChild))
                    else -> createSignatureText(e, e.identifier)
                }
            }
            is RsFile -> {
                val mName = e.modName
                if (e.isCrateRoot) "<i>crate</i>"
                else if (mName != null) "mod $mName"
                else "<i>file</i>"
            }
            is RsNamedElement -> createSignatureText(e, e.navigationElement)
            else -> null
        } ?: return null
        return "$formatString${e.locationString}"
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

    private val RsFunction.signatureText: String?
        get() = createSignatureText(this, identifier, listOf(whereClause, retType, valueParameterList))

    private val RsPatBinding.owner: PsiElement?
        get() = ancestors
            .drop(1).dropWhile { it is RsPat || it is RsMatchPat || it is RsPatField }
            .filterIsInstance<RsCompositeElement>()
            .firstOrNull()
    private val PsiElement.locationString: String
        get() = containingFile?.let { " [${it.name}]" }.orEmpty()

    private val String.escaped: String get() = StringUtil.escapeXml(this)

}
