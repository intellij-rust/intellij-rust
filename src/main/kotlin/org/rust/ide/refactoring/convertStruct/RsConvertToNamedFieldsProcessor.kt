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
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.RsMandatoryReferenceElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.descendantOfTypeStrict

class RsConvertToNamedFieldsProcessor(
    project: Project,
    val element: RsFieldsOwner,
    private val convertUsages: Boolean
) : BaseRefactoringProcessor(project) {
    constructor(project: Project,
                element: RsFieldsOwner,
                convertUsages: Boolean,
                newNames: List<String>
    ) : this(project, element, convertUsages) {
        this.newNames = newNames
    }

    private val rsPsiFactory: RsPsiFactory = RsPsiFactory(project)

    private val types = element.tupleFields!!.tupleFieldDeclList
    private var newNames: List<String> = (0..types.size).map { "_$it" }

    override fun findUsages(): Array<UsageInfo> {
        if (!convertUsages) return arrayOf()
        var usages = ReferencesSearch
            .search(element)
            .asSequence()
            .map { MyUsageInfo(it) }

        usages += types
            .mapIndexed { index, rsTupleFieldDecl ->
                ProgressManager.checkCanceled()
                ReferencesSearch.search(rsTupleFieldDecl)
                    .map { MyUsageInfo(it, newNames[index]) }
            }.flatten()

        return usages
            .toList()
            .toTypedArray()
    }

    private class MyUsageInfo(psiReference: PsiReference, val fieldName: String? = null) : UsageInfo(psiReference)

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        loop@ for (usage in usages) {
            val element = usage.element ?: continue
            when (val usageParent = element.parent) {
                is RsDotExpr,
                is RsStructLiteralBody,
                is RsPatField -> {
                    val nameElement = (element as RsMandatoryReferenceElement).referenceNameElement
                    nameElement.replace(rsPsiFactory.createIdentifier((usage as MyUsageInfo).fieldName!!))
                }
                is RsPatTupleStruct -> {
                    var firstPatRestIndex = Int.MAX_VALUE
                    var lastPatRestIndex = -1
                    for ((index, pat) in usageParent.patList.withIndex()) {
                        if (pat is RsPatRest) {
                            firstPatRestIndex = minOf(firstPatRestIndex, index)
                            lastPatRestIndex = maxOf(lastPatRestIndex, index)
                        }
                    }

                    val text = buildString {
                        append("let ${usageParent.path.text}{")
                        usageParent.patList.withIndex()
                            // Filter out all patterns between first and last `..` patterns.
                            // In most cases it skips only single `..` pattern
                            .filter { (index, _) -> index < firstPatRestIndex || index > firstPatRestIndex }
                            .joinTo(this, ", ") { (index, pat) ->
                                val fieldNumber = when {
                                    index < firstPatRestIndex -> index
                                    index > lastPatRestIndex -> index + types.size - usageParent.patList.size
                                    else -> error("Unreachable")
                                }
                                "${newNames[fieldNumber]}:${pat.text}"
                            }
                        // If there is at least one `..` pattern, we need to add the corresponding pattern in
                        if (lastPatRestIndex != -1) {
                            append(", ..")
                        }
                        append("} = 0;")
                    }

                    val patternPsiElement = rsPsiFactory
                        .createStatement(text)
                        .descendantOfTypeStrict<RsPatStruct>()!!
                    usageParent.replace(patternPsiElement)
                }
                //first parent doesn't work in case of RsCallExpr
                else -> {
                    val callExpr = usageParent
                        .ancestorOrSelf<RsCallExpr>(RsValueArgumentList::class.java)
                        ?: continue@loop

                    // we only want to convert constructors
                    if ((callExpr.expr as? RsPathExpr)?.path != element) {
                        continue@loop
                    }
                    val values = callExpr.valueArgumentList.exprList
                    val text = "let a = ${callExpr.expr.text}" +
                        values.zip(newNames)
                            .joinToString(",\n", "{", "};") { (expr, name) ->
                                "$name:${expr.text}"
                            }

                    val structCreationElement = rsPsiFactory
                        .createStatement(text)
                        .descendantOfTypeStrict<RsStructLiteral>()!!
                    callExpr.replace(structCreationElement)
                }
            }
        }

        //convert struct itself
        val fields = types
            .zip(newNames)
            .joinToString(",\n", "{", "}") { (tupleField, name) ->
                "${tupleField.text.substring(0, tupleField.typeReference.startOffsetInParent)}$name: ${tupleField.typeReference.text}"
            }
        val newFieldsElement = rsPsiFactory.createStruct("struct A$fields")

        element.tupleFields!!.replace(newFieldsElement.blockFields!!)
        (element as? RsStructItem)?.semicolon?.delete()

    }

    override fun getCommandName(): String {
        return "Converting ${element.name} to named fields"
    }

    override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor {
        return BaseUsageViewDescriptor(element)
    }

    override fun getRefactoringId(): String? {
        return "refactoring.convertToNamedFields"
    }
}
