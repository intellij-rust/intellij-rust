/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsMatchingVisitor(private val matchingVisitor: GlobalMatchingVisitor) : RsVisitor() {
    private fun getHandler(element: PsiElement): MatchingHandler =
        matchingVisitor.matchContext.pattern.getHandler(element)

    private fun matchTextOrVariable(templateElement: PsiElement?, treeElement: PsiElement?): Boolean {
        if (templateElement == null) return true
        if (treeElement == null) return false
        return when (val handler = getHandler(templateElement)) {
            is SubstitutionHandler -> handler.validate(treeElement, matchingVisitor.matchContext)
            else -> matchingVisitor.matchText(templateElement, treeElement)
        }
    }

    private inline fun <reified T> getElement(): T? = when (val element = matchingVisitor.element) {
        is T -> element
        else -> {
            matchingVisitor.result = false
            null
        }
    }

    private fun match(e1: PsiElement?, e2: PsiElement?) = matchingVisitor.match(e1, e2)

    override fun visitStructItem(o: RsStructItem) {
        val struct = getElement<RsStructItem>() ?: return
        matchingVisitor.result =
            matchOuterAttrList(o, struct) &&
                match(o.vis, struct.vis) &&
                matchIdentifier(o.identifier, struct.identifier) &&
                match(o.typeParameterList, struct.typeParameterList) &&
                match(o.whereClause, struct.whereClause) &&
                match(o.blockFields, struct.blockFields) &&
                match(o.tupleFields, struct.tupleFields) &&
                matchLeaf(o.union, struct.union)
    }

    override fun visitTypeParameterList(o: RsTypeParameterList) {
        val parameters = getElement<RsTypeParameterList>() ?: return
        matchingVisitor.result =
            matchingVisitor.matchInAnyOrder(o.typeParameterList, parameters.typeParameterList) &&
                matchingVisitor.matchInAnyOrder(o.lifetimeParameterList, parameters.lifetimeParameterList) &&
                matchingVisitor.matchInAnyOrder(o.constParameterList, parameters.constParameterList)
    }

    override fun visitWhereClause(o: RsWhereClause) {
        val where = getElement<RsWhereClause>() ?: return
        matchingVisitor.result = matchingVisitor.matchInAnyOrder(o.wherePredList, where.wherePredList)
    }

    override fun visitWherePred(o: RsWherePred) {
        val where = getElement<RsWherePred>() ?: return
        matchingVisitor.result = match(o.typeReference, where.typeReference)
            && match(o.typeParamBounds, where.typeParamBounds)
    }

    override fun visitTypeParameter(o: RsTypeParameter) {
        val parameter = getElement<RsTypeParameter>() ?: return
        matchingVisitor.result =
            matchOuterAttrList(o, parameter) &&
                match(o.typeParamBounds, parameter.typeParamBounds) &&
                match(o.typeReference, parameter.typeReference) &&
                matchIdentifier(o.identifier, parameter.identifier)
    }

    override fun visitTypeArgumentList(o: RsTypeArgumentList) {
        val list = getElement<RsTypeArgumentList>() ?: return
        matchingVisitor.result = matchingVisitor.matchSequentially(o.typeArguments, list.typeArguments)
    }

    override fun visitTypeParamBounds(o: RsTypeParamBounds) {
        val bounds = getElement<RsTypeParamBounds>() ?: return
        matchingVisitor.result = matchingVisitor.matchInAnyOrder(o.polyboundList, bounds.polyboundList)
    }

    override fun visitPolybound(o: RsPolybound) {
        val polybound = getElement<RsPolybound>() ?: return
        matchingVisitor.result = match(o.bound, polybound.bound)
            && match(o.forLifetimes, polybound.forLifetimes)
    }

    override fun visitBound(o: RsBound) {
        val bound = getElement<RsBound>() ?: return
        matchingVisitor.result = match(o.lifetime, bound.lifetime)
            && match(o.traitRef, bound.traitRef)
    }

    override fun visitTraitRef(o: RsTraitRef) {
        val trait = getElement<RsTraitRef>() ?: return
        matchingVisitor.result = match(o.path, trait.path)
    }

    override fun visitLifetimeParameter(o: RsLifetimeParameter) {
        val lifetime = getElement<RsLifetimeParameter>() ?: return
        matchingVisitor.result = matchIdentifier(o.quoteIdentifier, lifetime.quoteIdentifier)
    }

    override fun visitConstParameter(o: RsConstParameter) {
        val const = getElement<RsConstParameter>() ?: return
        matchingVisitor.result = match(o.typeReference, const.typeReference)
            && matchIdentifier(o.identifier, const.identifier)
    }

    override fun visitConstant(o: RsConstant) {
        val constant = getElement<RsConstant>() ?: return
        matchingVisitor.result = matchIdentifier(o.identifier, constant.identifier) &&
            match(o.vis, constant.vis) &&
            match(o.typeReference, constant.typeReference) &&
            match(o.expr, constant.expr) &&
            matchOuterAttrList(o, constant) &&
            matchLeaf(o.static, constant.static) &&
            matchLeaf(o.underscore, constant.underscore) &&
            matchLeaf(o.mut, constant.mut)
    }

    override fun visitBlockFields(o: RsBlockFields) {
        val fields = getElement<RsBlockFields>() ?: return
        matchingVisitor.result = matchingVisitor.matchSequentially(o.namedFieldDeclList, fields.namedFieldDeclList)
    }

    override fun visitNamedFieldDecl(o: RsNamedFieldDecl) {
        val field = getElement<RsNamedFieldDecl>() ?: return
        matchingVisitor.result =
            matchOuterAttrList(o, field) &&
                match(o.vis, field.vis) &&
                matchIdentifier(o.identifier, field.identifier) &&
                match(o.typeReference, field.typeReference)
    }

    override fun visitTupleFields(o: RsTupleFields) {
        val fields = getElement<RsTupleFields>() ?: return
        matchingVisitor.result = matchingVisitor.matchSequentially(o.tupleFieldDeclList, fields.tupleFieldDeclList)
    }

    override fun visitTupleFieldDecl(o: RsTupleFieldDecl) {
        val field = getElement<RsTupleFieldDecl>() ?: return
        matchingVisitor.result =
            matchOuterAttrList(o, field) &&
                match(o.vis, field.vis) &&
                match(o.typeReference, field.typeReference)
    }

    override fun visitTypeReference(o: RsTypeReference) {
        val typeReference = getElement<RsTypeReference>() ?: return
        // TODO: implement individual type references
        matchingVisitor.result = matchTextOrVariable(o, typeReference)
    }

    override fun visitRefLikeType(o: RsRefLikeType) {
        val refType = getElement<RsRefLikeType>() ?: return
        matchingVisitor.result = match(o.typeReference, refType.typeReference) &&
            match(o.lifetime, refType.lifetime) &&
            matchLeaf(o.mut, refType.mut) &&
            matchLeaf(o.const, refType.const) &&
            matchLeaf(o.mul, refType.mul) &&
            matchLeaf(o.and, refType.and)
    }

    override fun visitLifetime(o: RsLifetime) {
        val lifetime = getElement<RsLifetime>() ?: return
        matchingVisitor.result = matchTextOrVariable(o, lifetime)
    }

    override fun visitVis(o: RsVis) {
        val vis = getElement<RsVis>() ?: return
        matchingVisitor.result = matchTextOrVariable(o, vis)
    }

    override fun visitInnerAttr(o: RsInnerAttr) {
        val attr = getElement<RsInnerAttr>() ?: return
        matchingVisitor.result = match(o.metaItem, attr.metaItem)
    }

    override fun visitOuterAttr(o: RsOuterAttr) {
        val attr = getElement<RsOuterAttr>() ?: return
        matchingVisitor.result = match(o.metaItem, attr.metaItem)
    }

    override fun visitMetaItem(o: RsMetaItem) {
        val metaItem = getElement<RsMetaItem>() ?: return
        matchingVisitor.result = match(o.compactTT, metaItem.compactTT) &&
            match(o.litExpr, metaItem.litExpr) &&
            match(o.metaItemArgs, metaItem.metaItemArgs) &&
            match(o.path, metaItem.path)
    }

    override fun visitMetaItemArgs(o: RsMetaItemArgs) {
        val metaItemArgs = getElement<RsMetaItemArgs>() ?: return
        matchingVisitor.result = matchingVisitor.matchInAnyOrder(o.metaItemList, metaItemArgs.metaItemList)
    }

    override fun visitPathType(o: RsPathType) {
        val path = getElement<RsPathType>() ?: return
        matchingVisitor.result = match(o.path, path.path)
    }

    override fun visitPath(o: RsPath) {
        val path = getElement<RsPath>() ?: return

        matchingVisitor.result = matchIdentifier(o.identifier, path.identifier) &&
            match(o.typeArgumentList, path.typeArgumentList) &&
            match(o.valueParameterList, path.valueParameterList) &&
            match(o.path, path.path)
    }

    override fun visitLitExpr(o: RsLitExpr) {
        val litExpr = getElement<RsLitExpr>() ?: return
        matchingVisitor.result = o.booleanValue == litExpr.booleanValue &&
            o.integerValue == litExpr.integerValue &&
            o.floatValue == litExpr.floatValue &&
            o.charValue == litExpr.charValue &&
            o.stringValue == litExpr.stringValue
    }

    private fun matchOuterAttrList(e1: RsOuterAttributeOwner, e2: RsOuterAttributeOwner): Boolean {
        return matchingVisitor.matchInAnyOrder(e1.outerAttrList, e2.outerAttrList)
    }

    private fun matchIdentifier(templateIdentifier: PsiElement?, treeIdentifier: PsiElement?): Boolean {
        return matchTextOrVariable(templateIdentifier, treeIdentifier)
    }

    private fun matchLeaf(templateElement: PsiElement?, treeElement: PsiElement?): Boolean {
        return templateElement?.elementTypeOrNull == treeElement?.elementTypeOrNull
    }
}

private fun GlobalMatchingVisitor.matchSequentially(elements: List<PsiElement?>, elements2: List<PsiElement?>) =
    matchSequentially(elements.toTypedArray(), elements2.toTypedArray())

private fun GlobalMatchingVisitor.matchInAnyOrder(elements: List<PsiElement?>, elements2: List<PsiElement?>) =
    matchInAnyOrder(elements.toTypedArray(), elements2.toTypedArray())
