/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractStructFields

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.BaseUsageViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.rust.RsBundle
import org.rust.ide.inspections.lints.toSnakeCase
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.refactoring.RsInPlaceVariableIntroducer
import org.rust.ide.refactoring.findTransitiveAttributes
import org.rust.ide.refactoring.generate.StructMember
import org.rust.ide.refactoring.suggestedNames
import org.rust.ide.utils.GenericConstraints
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.RBRACE
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty

private sealed class RsStructUsage(usage: RsElement) : UsageInfo(usage) {
    data class LiteralUsage(val literal: RsStructLiteral) : RsStructUsage(literal)
    data class PatUsage(val pat: RsPatStruct) : RsStructUsage(pat)
    data class FieldUsage(val fieldLookup: RsFieldLookup) : RsStructUsage(fieldLookup)
}

class RsExtractStructFieldsProcessor(
    project: Project,
    private val editor: Editor,
    private val ctx: RsExtractStructFieldsContext
) : BaseRefactoringProcessor(project) {

    override fun findUsages(): Array<UsageInfo> {
        val structUsages = ReferencesSearch.search(ctx.struct).mapNotNull {
            val element = it.element
            when (val parent = element.parent) {
                is RsStructLiteral -> RsStructUsage.LiteralUsage(parent)
                is RsPatStruct -> RsStructUsage.PatUsage(parent)
                else -> null
            }
        }
        val fieldUsages = ctx.fields.flatMap {
            ReferencesSearch.search(it.field).mapNotNull { fieldUsage ->
                val lookup = fieldUsage.element as? RsFieldLookup ?: return@mapNotNull null
                RsStructUsage.FieldUsage(lookup)
            }
        }
        return (structUsages + fieldUsages).toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val struct = ctx.struct
        val project = struct.project
        val factory = RsPsiFactory(project)

        val newStruct = createStruct(factory, ctx)
        val inserted = struct.parent.addBefore(newStruct, struct) as RsStructItem
        val type = inserted.declaredType

        val newField = replaceFields(factory, ctx.name, struct, ctx.fields, type)
        val newFieldName = newField.name ?: return

        val fieldMap = ctx.fields.mapNotNull {
            val field = it.field as? RsNamedFieldDecl ?: return@mapNotNull null
            val name = field.name ?: return@mapNotNull null
            name to field
        }.toMap()

        val occurrences = mutableListOf<RsReferenceElement>()
        for (usage in usages) {
            val structUsage = usage as? RsStructUsage ?: continue
            val occurrence = replaceUsage(factory, structUsage, newFieldName, ctx.name, fieldMap) ?: continue
            occurrences.add(occurrence)
        }

        for (occurrence in occurrences) {
            if (occurrence.reference?.resolve() == null) {
                RsImportHelper.importElement(occurrence, inserted)
            }
        }

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        editor.caretModel.moveToOffset(newField.identifier.startOffset)
        RsInPlaceVariableIntroducer(newField, editor, project, RsBundle.message("command.name.choose.field.name")).performInplaceRefactoring(null)
    }

    override fun getCommandName(): String = RsBundle.message("action.Rust.RsExtractStructFields.command.name")

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
        BaseUsageViewDescriptor(ctx.struct)

    override fun getRefactoringId(): String = "refactoring.extractStructFields"

    companion object {
        private val TRANSITIVE_ATTRIBUTES: Set<String> = setOf("derive", "repr")

        private fun replaceFields(
            factory: RsPsiFactory,
            name: String,
            struct: RsStructItem,
            replacedFields: List<StructMember>,
            type: Ty
        ): RsNamedFieldDecl {
            val visibility = getBroadestVisibility(factory, replacedFields)
            val visibilityText = if (visibility != null) {
                "${visibility.text} "
            } else ""
            val fieldName = generateName(struct, name)
            val text = "$visibilityText${fieldName}: ${type.renderInsertionSafe(includeLifetimeArguments = true)}"

            val newField = factory.createStructNamedField(text)
            val firstField = replacedFields[0].field
            val insertedField = firstField.parent.addBefore(newField, firstField) as RsNamedFieldDecl
            insertedField.parent.addAfter(factory.createComma(), insertedField)

            replacedFields.forEach {
                it.field.deleteWithSurroundingCommaAndWhitespace()
            }
            return insertedField
        }

        private fun generateName(struct: RsStructItem, structName: String): String {
            val fieldName = structName.toSnakeCase(false)
            var name = fieldName
            var index = 0
            val fieldMap = struct.blockFields?.namedFieldDeclList?.map { it.name }?.toSet().orEmpty()
            while (name in fieldMap) {
                name = "$fieldName${index}"
                index += 1
            }
            return name
        }

        private fun createStruct(factory: RsPsiFactory, ctx: RsExtractStructFieldsContext): RsStructItem {
            val struct = ctx.struct
            val vis = struct.vis?.text
            val formattedVis = if (vis == null) "" else "$vis "
            val fieldsText = ctx.fields.joinToString(separator = ",\n") {
                it.field.text
            }
            val attributes = findTransitiveAttributes(struct, TRANSITIVE_ATTRIBUTES)

            val fieldTypeReferences = ctx.fields.mapNotNull { it.field.typeReference }
            val genericConstraints = GenericConstraints.create(struct)
                .filterByTypeReferences(fieldTypeReferences)

            val typeParameters = genericConstraints.buildTypeParameters()
            val whereClause = genericConstraints.buildWhereClause()

            val text = "${formattedVis}struct ${ctx.name}$typeParameters$whereClause {\n$fieldsText\n}"
            val attributesText = if (attributes.isNotEmpty()) {
                attributes.joinToString(separator = "\n", postfix = "\n") { it.text }
            } else ""
            return factory.createStruct("$attributesText$text")
        }

        /**
         * Find the visibility for the field containing the newly extracted struct.
         * If any of the extracted fields were pub, use pub.
         * Otherwise, if any of the extracted fields were pub(crate), use pub(crate).
         * Otherwise, find the ancestor module that encompasses all of the extracted fields.
         */
        private fun getBroadestVisibility(factory: RsPsiFactory, replacedFields: List<StructMember>): RsVis? {
            val visibilities = replacedFields.mapNotNull { it.field.vis }
            val pubVis = visibilities.find { it.visibility == RsVisibility.Public }
            if (pubVis != null) return pubVis

            val crateVis = visibilities.find {
                val visibility = it.visibility
                visibility is RsVisibility.Restricted && visibility.inMod.isCrateRoot
            }
            if (crateVis != null) return crateVis

            var broadest: RsMod? = null
            for (vis in visibilities) {
                val visibility = vis.visibility
                if (visibility is RsVisibility.Restricted) {
                    val module = visibility.inMod
                    broadest = if (broadest == null) {
                        module
                    } else {
                        commonParentMod(broadest, module) ?: return factory.createVis("pub(crate)")
                    }
                }
            }
            if (broadest != null) {
                return factory.createVis("pub(in ${broadest.qualifiedName})")
            }
            return null
        }

        private fun replaceUsage(
            factory: RsPsiFactory,
            usage: RsStructUsage,
            fieldName: String,
            structName: String,
            fields: Map<String, RsFieldDecl>
        ): RsReferenceElement? = when (usage) {
            is RsStructUsage.LiteralUsage -> replaceLiteralUsage(factory, usage.literal, fieldName, structName, fields)
            is RsStructUsage.PatUsage -> replacePatUsage(factory, usage.pat, fieldName, structName, fields)
            is RsStructUsage.FieldUsage -> replaceFieldUsage(factory, usage.fieldLookup, fieldName)
        }

        private fun replaceLiteralUsage(
            factory: RsPsiFactory,
            literal: RsStructLiteral,
            fieldName: String,
            structName: String,
            fields: Map<String, RsFieldDecl>
        ): RsReferenceElement {
            val foundFields = mutableSetOf<String>()
            val fieldExprs = mutableListOf<RsStructLiteralField>()
            val body = literal.structLiteralBody
            for (field in body.structLiteralFieldList) {
                val name = field.identifier?.text ?: continue
                if (name in fields) {
                    fieldExprs.add(field)
                    foundFields.add(name)
                }
            }
            if (fieldExprs.size < fields.size) {
                // Some fields were not found, try to map them from ..
                val expr = body.expr
                val dotdot = body.dotdot
                if (dotdot != null && expr != null) {
                    val extractedExpr = extractDotDotExpr(factory, literal, expr)
                    val missingKeys = fields.keys - foundFields
                    for (key in missingKeys) {
                        // Access the field through the new substruct
                        val literalField = factory.createStructLiteralField(key, "${extractedExpr.text}.$fieldName.$key")
                        fieldExprs.add(literalField)
                    }
                    expr.replace(extractedExpr)
                }
            }

            val newLiteral = factory.createStructLiteral(structName, "{ ${fieldExprs.joinToString(", ") { it.text }} }")
            val newField = factory.createStructLiteralField(fieldName, newLiteral)
            val anchor = fieldExprs.firstOrNull { it.isPhysical } ?: body.dotdot ?: body.expr ?: body.rbrace
            val inserted = body.addBefore(newField, anchor) as RsStructLiteralField

            fieldExprs.forEach {
                it.deleteWithSurroundingCommaAndWhitespace()
            }

            if (inserted.getNextNonCommentSibling()?.elementType != RBRACE) {
                inserted.parent.addAfter(factory.createComma(), inserted)
            }

            return (inserted.expr as RsStructLiteral).path
        }

        private fun replacePatUsage(
            factory: RsPsiFactory,
            pat: RsPatStruct,
            fieldName: String,
            structName: String,
            fields: Map<String, RsFieldDecl>
        ): RsReferenceElement {
            val fieldPats = pat.patFieldList.filter {
                val patFieldFull = it.patFieldFull
                val patBinding = it.patBinding
                val name = when {
                    patFieldFull != null -> patFieldFull.identifier?.text.orEmpty()
                    patBinding != null -> patBinding.identifier.text
                    else -> return@filter false
                }
                name in fields
            }
            val rest = if (fieldPats.size < fields.size) {
                factory.createPatRest()
            } else {
                null
            }

            val newStructPat = factory.createPatStruct(structName, fieldPats, rest)
            val newFieldPat = factory.createPatFieldFull(fieldName, newStructPat.text)
            val anchor = fieldPats.firstOrNull() ?: pat.patRest ?: pat.rbrace
            val inserted = pat.addBefore(newFieldPat, anchor) as RsPatFieldFull

            fieldPats.forEach {
                it.deleteWithSurroundingCommaAndWhitespace()
            }

            if (inserted.getNextNonCommentSibling()?.elementType != RBRACE) {
                inserted.parent.addAfter(factory.createComma(), inserted)
            }

            return (inserted.pat as RsPatStruct).path
        }

        private fun replaceFieldUsage(
            factory: RsPsiFactory,
            fieldLookup: RsFieldLookup,
            fieldName: String
        ): RsReferenceElement? {
            val dotExpr = fieldLookup.parentDotExpr
            val newDotExpr = factory.createExpression("${dotExpr.expr.text}.$fieldName")
            dotExpr.expr.replace(newDotExpr)
            return null
        }

        /**
         * Either return the dotdot expression directly or extract it into a local variable and return a reference to it.
         */
        private fun extractDotDotExpr(factory: RsPsiFactory, literal: RsStructLiteral, expr: RsExpr): RsExpr {
            // Do not create a new variable if the expression already refers to one
            if (expr is RsPathExpr) return expr

            val name = expr.suggestedNames().default
            val variable = factory.createLetDeclaration(name, expr)

            val anchor = literal.parentOfType<RsStmt>() ?: literal
            anchor.parent.addBefore(variable, anchor)
            return factory.createExpression(name)
        }
    }
}
