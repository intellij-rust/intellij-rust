/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.convertStruct

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.BaseUsageViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsConvertToTupleProcessor(project: Project, val element: RsFieldsOwner, val convertUsages: Boolean = true) : BaseRefactoringProcessor(project) {
    private val rsPsiFactory = RsPsiFactory(project)
    private val fieldDeclList = element.blockFields!!.namedFieldDeclList
    override fun findUsages(): Array<UsageInfo> {
        if (!convertUsages) return arrayOf()

        var usages = ReferencesSearch
            .search(element)
            .asSequence()
            .map { UsageInfo(it) }

        usages += fieldDeclList
            .mapIndexed { index, rsNamedFieldDecl ->
                ProgressManager.checkCanceled()
                ReferencesSearch
                    .search(rsNamedFieldDecl)
                    //other references will be handled from main struct usages
                    .filter { it.element.parent is RsDotExpr }
                    .map { MyUsageInfo(it, index) }
            }.flatten()

        return usages
            .toList()
            .toTypedArray()
    }

    private class MyUsageInfo(psiReference: PsiReference, val position: Int) : UsageInfo(psiReference)

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        for (usage in usages) {
            val element = usage.element ?: continue
            when (val usageParent = element.parent) {
                is RsDotExpr ->
                    usage.element!!.replace(rsPsiFactory
                        .createExpression("a.${(usage as MyUsageInfo).position}")
                        .descendantOfTypeStrict<RsFieldLookup>()!!)
                is RsPatStruct -> {
                    val patternFieldMap = usageParent.patFieldList
                        .map { it.kind }
                        .associate { kind ->
                            when (kind) {
                                is RsPatFieldKind.Full -> kind.fieldName to kind.pat.text
                                is RsPatFieldKind.Shorthand -> kind.fieldName to kind.binding.text

                            }
                        }
                    val text = "let ${usageParent.path.text}" +
                        fieldDeclList.joinToString(", ", "(", ") = 0;") {
                            patternFieldMap[it.identifier.text] ?: "_ "
                        }

                    val patternPsiElement = rsPsiFactory
                        .createStatement(text)
                        .descendantOfTypeStrict<RsPatTupleStruct>()!!

                    usageParent.replace(patternPsiElement)
                }
                is RsStructLiteral -> {
                    if (usageParent.structLiteralBody.dotdot != null) {
                        val text = "let a = ${usageParent.path.text}{" +
                            usageParent.structLiteralBody.structLiteralFieldList.joinToString(",") {
                                "${fieldDeclList.indexOfFirst { inner -> inner.identifier.textMatches(it.identifier!!) }}:${it.expr?.text
                                    ?: it.identifier!!.text}"
                            } + ", ..${usageParent.structLiteralBody.expr!!.text}};"

                        val newElement = rsPsiFactory
                            .createStatement(text)
                            .descendantOfTypeStrict<RsStructLiteral>()!!

                        usageParent.replace(newElement)
                    } else {
                        //map to restore order of fields
                        val valuesMap = usageParent.structLiteralBody.structLiteralFieldList
                            .associate { it.identifier!!.text to (it.expr?.text ?: it.identifier!!.text) }

                        val text = "let a = ${usageParent.path.text}" +
                            fieldDeclList.joinToString(", ", "(", ");") { valuesMap[it.identifier.text] ?: "_ " }

                        val newElement = rsPsiFactory
                            .createStatement(text)
                            .descendantOfTypeStrict<RsCallExpr>()!!

                        usageParent.replace(newElement)
                    }
                }
            }
        }

        val types = fieldDeclList
            .mapNotNull { "${it.text.substring(0, it.identifier.startOffsetInParent)}${it.typeReference?.text}" }
            .joinToString(",", "(", ")")

        val newTuplePsiElement = rsPsiFactory.createStruct("struct A$types;")

        element.blockFields!!.replace(newTuplePsiElement.tupleFields!!)
        (element as? RsStructItem)?.addAfter(rsPsiFactory.createSemicolon(), element.tupleFields!!)
    }

    override fun getCommandName(): String {
        return "Converting ${element.name} to tuple"
    }

    override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor {
        return BaseUsageViewDescriptor(element)
    }

    override fun getRefactoringId(): String? {
        return "refactoring.convertToTuple"
    }
}
