/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler
import org.rust.ide.utils.import.ImportCandidatesCollector
import org.rust.ide.utils.import.ImportContext
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.stringValue

class RsMatchingVisitor(private val myMatchingVisitor: GlobalMatchingVisitor) : RsVisitor() {
    private fun getHandler(element: PsiElement) = myMatchingVisitor.matchContext.pattern.getHandler(element)

    private fun matchTextOrVariable(treeElement: PsiElement?, patternElement: PsiElement?): Boolean {
        if (treeElement == null) return true
        if (patternElement == null) return treeElement == patternElement
        return when (val handler = getHandler(treeElement)) {
            is SubstitutionHandler -> handler.validate(patternElement, myMatchingVisitor.matchContext)
            else -> myMatchingVisitor.matchText(treeElement, patternElement)
        }
    }

    private inline fun <reified T> getElement(): T? = when (val element = myMatchingVisitor.element) {
        is T -> element
        else -> {
            myMatchingVisitor.result = false
            null
        }
    }

    private fun match(e1: PsiElement?, e2: PsiElement?) = myMatchingVisitor.match(e1, e2)

    override fun visitStructItem(o: RsStructItem) {
        val struct = getElement<RsStructItem>() ?: return
        myMatchingVisitor.result =
            matchOuterAttrList(o.outerAttrList, struct.outerAttrList) &&
                match(o.vis, struct.vis) &&
                matchIdentifier(o.identifier, struct.identifier) &&
                match(o.typeParameterList, struct.typeParameterList) &&
                match(o.whereClause, struct.whereClause) &&
                match(o.blockFields, struct.blockFields) &&
                match(o.tupleFields, struct.tupleFields)
    }

    override fun visitTypeParameterList(o: RsTypeParameterList) {
        val parameters = getElement<RsTypeParameterList>() ?: return
        myMatchingVisitor.result =
            myMatchingVisitor.matchSequentially(o.typeParameterList, parameters.typeParameterList) &&
                myMatchingVisitor.matchSequentially(o.lifetimeParameterList, parameters.lifetimeParameterList) &&
                myMatchingVisitor.matchSequentially(o.constParameterList, parameters.constParameterList)
    }

    override fun visitWhereClause(o: RsWhereClause) {
        val where = getElement<RsWhereClause>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchInAnyOrder(o.wherePredList, where.wherePredList)
    }

    override fun visitWherePred(o: RsWherePred) {
        val where = getElement<RsWherePred>() ?: return
        myMatchingVisitor.result = match(o.typeReference, where.typeReference)
            && match(o.typeParamBounds, where.typeParamBounds)
    }

    override fun visitTypeParameter(o: RsTypeParameter) {
        val parameter = getElement<RsTypeParameter>() ?: return
        myMatchingVisitor.result =
            matchOuterAttrList(o.outerAttrList, parameter.outerAttrList) &&
                match(o.typeParamBounds, parameter.typeParamBounds) &&
                match(o.typeReference, parameter.typeReference) &&
                matchIdentifier(o.identifier, parameter.identifier)
    }

    override fun visitTypeArgumentList(o: RsTypeArgumentList) {
        val list = getElement<RsTypeArgumentList>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSequentially(o.typeArguments, list.typeArguments)
    }

    override fun visitTypeParamBounds(o: RsTypeParamBounds) {
        val bounds = getElement<RsTypeParamBounds>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchInAnyOrder(o.polyboundList, bounds.polyboundList)
    }

    override fun visitPolybound(o: RsPolybound) {
        val polybound = getElement<RsPolybound>() ?: return
        myMatchingVisitor.result = match(o.bound, polybound.bound)
            && match(o.forLifetimes, polybound.forLifetimes)
    }

    override fun visitBound(o: RsBound) {
        val bound = getElement<RsBound>() ?: return
        myMatchingVisitor.result = match(o.lifetime, bound.lifetime)
            && match(o.traitRef, bound.traitRef)
    }

    override fun visitTraitRef(o: RsTraitRef) {
        val trait = getElement<RsTraitRef>() ?: return
        myMatchingVisitor.result = match(o.path, trait.path)
    }

    override fun visitLifetimeParameter(o: RsLifetimeParameter) {
        val lifetime = getElement<RsLifetimeParameter>() ?: return
        myMatchingVisitor.result = matchIdentifier(o.quoteIdentifier, lifetime.quoteIdentifier)
    }

    override fun visitConstParameter(o: RsConstParameter) {
        val const = getElement<RsConstParameter>() ?: return
        myMatchingVisitor.result = match(o.typeReference, const.typeReference)
            && matchIdentifier(o.identifier, const.identifier)
    }

    override fun visitConstant(o: RsConstant) {
        val constant = getElement<RsConstant>() ?: return
        myMatchingVisitor.result = matchIdentifier(o.identifier, constant.identifier) &&
            match(o.typeReference, constant.typeReference) &&
            match(o.expr, constant.expr)
    }

    override fun visitBlockFields(o: RsBlockFields) {
        val fields = getElement<RsBlockFields>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSequentially(o.namedFieldDeclList, fields.namedFieldDeclList)
    }

    override fun visitNamedFieldDecl(o: RsNamedFieldDecl) {
        val field = getElement<RsNamedFieldDecl>() ?: return
        myMatchingVisitor.result =
            matchOuterAttrList(o.outerAttrList, field.outerAttrList) &&
                match(o.vis, field.vis) &&
                matchIdentifier(o.identifier, field.identifier) &&
                match(o.typeReference, field.typeReference)
    }

    override fun visitTupleFields(o: RsTupleFields) {
        val fields = getElement<RsTupleFields>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSequentially(o.tupleFieldDeclList, fields.tupleFieldDeclList)
    }

    override fun visitTupleFieldDecl(o: RsTupleFieldDecl) {
        val field = getElement<RsTupleFieldDecl>() ?: return
        myMatchingVisitor.result =
            matchOuterAttrList(o.outerAttrList, field.outerAttrList) &&
                match(o.vis, field.vis) &&
                match(o.typeReference, field.typeReference)
    }

    override fun visitTypeReference(o: RsTypeReference) {
        val typeReference = getElement<RsTypeReference>() ?: return
        // TODO: implement individual type references
        myMatchingVisitor.result = matchTextOrVariable(o, typeReference)
    }

    override fun visitRefLikeType(o: RsRefLikeType) {
        val refType = getElement<RsRefLikeType>() ?: return
        myMatchingVisitor.result = (o.mut == null) == (refType.mut == null) &&
                (o.const == null) == (refType.const == null) &&
                (o.mul == null) == (refType.mul == null) &&
                match(o.typeReference, refType.typeReference) &&
                match(o.lifetime, refType.lifetime)
    }

    override fun visitLifetime(o: RsLifetime) {
        val lifetime = getElement<RsLifetime>() ?: return
        myMatchingVisitor.result = matchTextOrVariable(o, lifetime)
    }

    override fun visitVis(o: RsVis) {
        val vis = getElement<RsVis>() ?: return
        myMatchingVisitor.result = matchTextOrVariable(o, vis)
    }

    override fun visitInnerAttr(o: RsInnerAttr) {
        val attr = getElement<RsInnerAttr>() ?: return
        myMatchingVisitor.result = match(o.metaItem, attr.metaItem)
    }

    override fun visitOuterAttr(o: RsOuterAttr) {
        val attr = getElement<RsOuterAttr>() ?: return
        myMatchingVisitor.result = match(o.metaItem, attr.metaItem)
    }

    override fun visitMetaItem(o: RsMetaItem) {
        val metaItem = getElement<RsMetaItem>() ?: return
        myMatchingVisitor.result = match(o.compactTT, metaItem.compactTT) &&
            match(o.litExpr, metaItem.litExpr) &&
            match(o.metaItemArgs, metaItem.metaItemArgs) &&
            match(o.path, metaItem.path)
    }

    override fun visitMetaItemArgs(o: RsMetaItemArgs) {
        val metaItemArgs = getElement<RsMetaItemArgs>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchInAnyOrder(o.metaItemList, metaItemArgs.metaItemList)
    }

    override fun visitPathType(o: RsPathType) {
        val path = getElement<RsPathType>() ?: return

        // Match the last path component
        val lastPartMatching = matchIdentifier(o.path.identifier, path.path.identifier) &&
            match(o.path.typeArgumentList, path.path.typeArgumentList) &&
            match(o.path.valueParameterList, path.path.valueParameterList)
        if (!lastPartMatching) {
            myMatchingVisitor.result = false
            return
        }

        // Perfect match
        if (match(o.path.path, path.path.path)) {
            myMatchingVisitor.result = true
            return
        }

        // Match a corresponding import candidate
        val codeReference = path.path.reference?.resolve()
        val codeIdentifier = path.path.identifier?.text
        val context = ImportContext.from(path, ImportContext.Type.OTHER)
        if (context == null || codeIdentifier == null) {
            myMatchingVisitor.result = false
            return
        }
        val target = o.text.split("::").dropLast(1).joinToString("::")
        myMatchingVisitor.result = ImportCandidatesCollector
            .getImportCandidates(context, codeIdentifier)
            .filter { it.item == codeReference }
            .any { it.path.dropLast(1).joinToString("::") == target }
    }

    override fun visitPath(o: RsPath) {
        val path = getElement<RsPath>() ?: return
        myMatchingVisitor.result = matchIdentifier(o.identifier, path.identifier) &&
            match(o.typeArgumentList, path.typeArgumentList) &&
            match(o.valueParameterList, path.valueParameterList) &&
            match(o.path, path.path)
    }

    override fun visitLitExpr(o: RsLitExpr) {
        val litExpr = getElement<RsLitExpr>() ?: return
        myMatchingVisitor.result = o.booleanValue == litExpr.booleanValue &&
            o.integerValue == litExpr.integerValue &&
            o.floatValue == litExpr.floatValue &&
            o.charValue == litExpr.charValue &&
            o.stringValue == litExpr.stringValue
    }

    private fun matchOuterAttrList(treeAttrList: List<RsOuterAttr>, patternAttrList: List<RsOuterAttr>): Boolean = myMatchingVisitor.matchInAnyOrder(treeAttrList, patternAttrList)

    private fun matchIdentifier(treeIdentifier: PsiElement?, patternIdentifier: PsiElement?): Boolean = matchTextOrVariable(treeIdentifier, patternIdentifier)
}

private fun GlobalMatchingVisitor.matchSequentially(elements: List<PsiElement?>, elements2: List<PsiElement?>) =
    matchSequentially(elements.toTypedArray(), elements2.toTypedArray())

private fun GlobalMatchingVisitor.matchInAnyOrder(elements: List<PsiElement?>, elements2: List<PsiElement?>) =
    matchInAnyOrder(elements.toTypedArray(), elements2.toTypedArray())
