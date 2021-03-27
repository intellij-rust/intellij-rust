/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler
import org.rust.lang.core.psi.*

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

    override fun visitStructItem(o: RsStructItem) {
        val struct = getElement<RsStructItem>() ?: return
        myMatchingVisitor.result =
            matchOuterAttrList(o.outerAttrList, struct.outerAttrList) &&
                myMatchingVisitor.match(o.vis, struct.vis) &&
                matchIdentifier(o.identifier, struct.identifier) &&
                myMatchingVisitor.match(o.typeParameterList, struct.typeParameterList) &&
                myMatchingVisitor.match(o.whereClause, struct.whereClause) &&
                myMatchingVisitor.match(o.blockFields, struct.blockFields) &&
                myMatchingVisitor.match(o.tupleFields, struct.tupleFields)
    }

    override fun visitTypeParameterList(o: RsTypeParameterList) {
        val parameters = getElement<RsTypeParameterList>() ?: return
        myMatchingVisitor.result =
            myMatchingVisitor.matchInAnyOrder(o.typeParameterList, parameters.typeParameterList) &&
                myMatchingVisitor.matchInAnyOrder(o.lifetimeParameterList, parameters.lifetimeParameterList) &&
                myMatchingVisitor.matchInAnyOrder(o.constParameterList, parameters.constParameterList)
    }

    override fun visitWhereClause(o: RsWhereClause) {
        val where = getElement<RsWhereClause>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchInAnyOrder(o.wherePredList, where.wherePredList)
    }

    override fun visitWherePred(o: RsWherePred) {
        // TODO
        super.visitWherePred(o)
    }

    override fun visitTypeParameter(o: RsTypeParameter) {
        val parameter = getElement<RsTypeParameter>() ?: return
        myMatchingVisitor.result =
            matchOuterAttrList(o.outerAttrList, parameter.outerAttrList) &&
                myMatchingVisitor.match(o.typeParamBounds, parameter.typeParamBounds) &&
                myMatchingVisitor.match(o.typeReference, parameter.typeReference) &&
                matchIdentifier(o.identifier, parameter.identifier)
    }

    override fun visitTypeParamBounds(o: RsTypeParamBounds) {
        // TODO
        super.visitTypeParamBounds(o)
    }

    override fun visitLifetimeParameter(o: RsLifetimeParameter) {
        // TODO
        super.visitLifetimeParameter(o)
    }

    override fun visitConstParameter(o: RsConstParameter) {
        // TODO
        super.visitConstParameter(o)
    }

    override fun visitBlockFields(o: RsBlockFields) {
        val fields = getElement<RsBlockFields>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSequentially(o.namedFieldDeclList, fields.namedFieldDeclList)
    }

    override fun visitNamedFieldDecl(o: RsNamedFieldDecl) {
        val field = getElement<RsNamedFieldDecl>() ?: return
        myMatchingVisitor.result =
            matchOuterAttrList(o.outerAttrList, field.outerAttrList) &&
                myMatchingVisitor.match(o.vis, field.vis) &&
                matchIdentifier(o.identifier, field.identifier) &&
                myMatchingVisitor.match(o.typeReference, field.typeReference)
    }

    override fun visitTupleFields(o: RsTupleFields) {
        val fields = getElement<RsTupleFields>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.matchSequentially(o.tupleFieldDeclList, fields.tupleFieldDeclList)
    }

    override fun visitTupleFieldDecl(o: RsTupleFieldDecl) {
        val field = getElement<RsTupleFieldDecl>() ?: return
        myMatchingVisitor.result =
            matchOuterAttrList(o.outerAttrList, field.outerAttrList) &&
                myMatchingVisitor.match(o.vis, field.vis) &&
                myMatchingVisitor.match(o.typeReference, field.typeReference)
    }

    override fun visitTypeReference(o: RsTypeReference) {
        val typeReference = getElement<RsTypeReference>() ?: return
        // TODO: implement individual type references
        myMatchingVisitor.result = matchTextOrVariable(o, typeReference)
    }

    override fun visitRefLikeType(o: RsRefLikeType) {
        val refType = getElement<RsRefLikeType>() ?: return
        myMatchingVisitor.result =
            matchIdentifier(o.mut, refType.mut) &&
                matchIdentifier(o.const, refType.const) &&
                matchIdentifier(o.mul, refType.mul) &&
                matchIdentifier(o.and, refType.and) &&
                myMatchingVisitor.match(o.typeReference, refType.typeReference) &&
                myMatchingVisitor.match(o.lifetime, refType.lifetime)
    }

    override fun visitLifetime(o: RsLifetime) {
        val lifetime = getElement<RsLifetime>() ?: return
        myMatchingVisitor.result = matchTextOrVariable(o, lifetime)
    }

    override fun visitVis(o: RsVis) {
        val vis = getElement<RsVis>() ?: return
        myMatchingVisitor.result = matchTextOrVariable(o, vis)
    }

    override fun visitOuterAttr(o: RsOuterAttr) {
        val attr = getElement<RsOuterAttr>() ?: return
        myMatchingVisitor.result = myMatchingVisitor.match(o.metaItem, attr.metaItem)
    }

    override fun visitMetaItem(o: RsMetaItem) {
        val metaItem = getElement<RsMetaItem>() ?: return
        // TODO
        myMatchingVisitor.result =
            myMatchingVisitor.match(o.compactTT, metaItem.compactTT) &&
                myMatchingVisitor.match(o.litExpr, metaItem.litExpr) &&
                myMatchingVisitor.match(o.metaItemArgs, metaItem.metaItemArgs) &&
                myMatchingVisitor.match(o.path, metaItem.path) &&
                myMatchingVisitor.match(o.eq, metaItem.eq)
    }

    override fun visitPath(o: RsPath) {
        val path = getElement<RsPath>() ?: return
        // TODO
        myMatchingVisitor.result = matchIdentifier(o.identifier, path.identifier)
    }

    private fun matchOuterAttrList(treeAttrList: List<RsOuterAttr>, patternAttrList: List<RsOuterAttr>): Boolean = myMatchingVisitor.matchInAnyOrder(treeAttrList, patternAttrList)

    private fun matchIdentifier(treeIdentifier: PsiElement?, patternIdentifier: PsiElement?): Boolean = matchTextOrVariable(treeIdentifier, patternIdentifier)
}

private fun GlobalMatchingVisitor.matchSequentially(elements: List<PsiElement?>, elements2: List<PsiElement?>) =
    matchSequentially(elements.toTypedArray(), elements2.toTypedArray())

private fun GlobalMatchingVisitor.matchInAnyOrder(elements: List<PsiElement?>, elements2: List<PsiElement?>) =
    matchInAnyOrder(elements.toTypedArray(), elements2.toTypedArray())
