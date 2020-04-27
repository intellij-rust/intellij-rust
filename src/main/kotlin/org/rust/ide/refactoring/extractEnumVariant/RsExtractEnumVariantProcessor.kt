/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractEnumVariant

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.BaseUsageViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.rust.ide.inspections.import.RsImportHelper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.descendantOfTypeStrict
import org.rust.lang.core.psi.ext.parentEnum

class RsExtractEnumVariantProcessor(project: Project,
                                    private val editor: Editor,
                                    private val ctx: RsEnumVariant) : BaseRefactoringProcessor(project) {
    override fun findUsages(): Array<UsageInfo> {
        return ReferencesSearch.search(ctx).map { UsageInfo(it) }.toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val project = ctx.project
        val factory = RsPsiFactory(project)
        val element = createElement(ctx, factory)

        val name = ctx.name ?: return
        val occurrences = mutableListOf<RsReferenceElement>()
        for (usage in usages) {
            val reference = usage.element ?: continue
            val occurrence = element.replaceUsage(reference, name) as? RsReferenceElement ?: continue
            occurrences.add(occurrence)
        }

        val enum = ctx.parentEnum
        val parameters = filterTypeParameters(element.typeReferences, enum.typeParameterList)
        val typeParametersText = parameters.format()
        val whereClause = buildWhereClause(enum.whereClause, parameters)

        val struct = element.createStruct(enum.vis?.text, name, typeParametersText, whereClause)
        val inserted = enum.parent.addBefore(struct, enum) as RsStructItem
        for (occurrence in occurrences) {
            if (occurrence.reference?.resolve() == null) {
                RsImportHelper.importElements(occurrence, setOf(inserted))
            }
        }
        val tupleField = RsPsiFactory.TupleField(
            inserted.declaredType,
            addPub = false // enum variant's fields are pub by default
        )
        val newFields = factory.createTupleFields(listOf(tupleField))
        val replaced = element.toBeReplaced.replace(newFields) as RsTupleFields
        replaced.descendantOfTypeStrict<RsPath>()?.let { occurrences.add(it) }

        val additionalElementsToRename = occurrences.filter { it.reference?.resolve() != inserted }
        offerStructRename(project, editor, inserted, additionalElementsToRename)
    }

    override fun getCommandName(): String {
        return "Extracting variant ${ctx.name}"
    }

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return BaseUsageViewDescriptor(ctx)
    }

    override fun getRefactoringId(): String? {
        return "refactoring.extractEnumVariant"
    }
}
