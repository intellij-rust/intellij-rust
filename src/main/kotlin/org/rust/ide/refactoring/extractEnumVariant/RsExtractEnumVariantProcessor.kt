/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractEnumVariant

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.BaseUsageViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import org.rust.ide.inspections.import.RsImportHelper
import org.rust.ide.refactoring.RsInPlaceVariableIntroducer
import org.rust.ide.refactoring.findBinding
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.infer.hasCtConstParameters
import org.rust.lang.core.types.infer.hasTyTypeParameters
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type

class RsExtractEnumVariantProcessor(
    project: Project,
    private val editor: Editor,
    private val ctx: RsEnumVariant
) : BaseRefactoringProcessor(project) {
    override fun findUsages(): Array<UsageInfo> = ReferencesSearch.search(ctx).map {
        UsageInfo(it)
    }.toTypedArray()

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
        val attributes = findTransitiveAttributes(enum, TRANSITIVE_ATTRIBUTES)

        val struct = element.createStruct(
            enum.vis?.text,
            name,
            typeParametersText,
            whereClause,
            attributes.map { it.text }
        )
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

    companion object {
        val TRANSITIVE_ATTRIBUTES = setOf("derive", "repr")
    }
}

private sealed class VariantElement(val toBeReplaced: PsiElement, val factory: RsPsiFactory) {
    abstract val typeReferences: List<RsTypeReference>

    fun createStruct(
        vis: String?,
        name: String,
        typeParameters: String,
        whereClause: String,
        attributes: List<String>
    ): RsStructItem {
        val formattedVis = if (vis == null) "" else "$vis "
        val fieldsText = addPubToFields(toBeReplaced, factory).text
        val text = when (this) {
            is TupleVariant -> "${formattedVis}struct $name$typeParameters$fieldsText$whereClause;"
            is StructVariant -> "${formattedVis}struct $name$typeParameters$whereClause$fieldsText"
        }
        val textWithAttributes = "${attributes.joinToString(separator = "\n")}\n$text"
        return factory.createStruct(textWithAttributes)
    }

    abstract fun replaceUsage(element: PsiElement, name: String): PsiElement?

    companion object {
        protected fun addPubToFields(fields: PsiElement, factory: RsPsiFactory): PsiElement {
            require(fields is RsBlockFields || fields is RsTupleFields)

            val decls: List<RsFieldDecl> = when (fields) {
                is RsBlockFields -> fields.namedFieldDeclList
                is RsTupleFields -> fields.tupleFieldDeclList
                else -> error("unreachable")
            }

            val declsWithoutVis = decls.filter { it.vis == null }
            if (declsWithoutVis.isNotEmpty()) {
                val pub = factory.createPub()
                for (field in declsWithoutVis) {
                    when (field) {
                        is RsNamedFieldDecl -> field.addBefore(pub, field.identifier)
                        is RsTupleFieldDecl -> field.addBefore(pub, field.typeReference)
                    }
                }
            }

            return fields
        }
    }
}

private class TupleVariant(private val fields: RsTupleFields, factory: RsPsiFactory) : VariantElement(fields, factory) {
    override val typeReferences: List<RsTypeReference> = fields.tupleFieldDeclList.map { it.typeReference }

    override fun replaceUsage(element: PsiElement, name: String): PsiElement? {
        val pathExpr = element.parent as? RsPathExpr
        val replacedUsage = if (pathExpr != null) {
            val call = when (val parent = pathExpr.parent) {
                // constructor call
                is RsCallExpr -> replaceTupleCall(parent, name)
                // constructor reference
                else -> replaceTupleConstructor(pathExpr, name)
            }
            call.valueArgumentList.exprList.firstOrNull()
        } else {
            val parent = element.parentOfType<RsPatTupleStruct>() ?: return null
            val replacedParent = replaceTuplePattern(parent, name)
            replacedParent.patList.firstOrNull()
        }
        return replacedUsage?.descendantOfTypeOrSelf<RsReferenceElement>()
    }

    private fun replaceTupleConstructor(element: RsPathExpr, name: String): RsCallExpr {
        val args = (0 until fields.tupleFieldDeclList.size).map { "p$it" }
        val argumentsText = args.joinToString(",")
        val call = factory.createFunctionCall(name, argumentsText)
        val binding = factory.createFunctionCall(element.path.text, listOf(call))

        val lambda = factory.createExpression("|$argumentsText| ${binding.text}")
        val replaced = element.replace(lambda) as RsLambdaExpr
        return replaced.expr as RsCallExpr
    }

    private fun replaceTuplePattern(element: RsPatTupleStruct, name: String): RsPatTupleStruct {
        val innerPat = factory.createPatTupleStruct(name, element.patList)
        val binding = factory.createPatTupleStruct(element.path.text, listOf(innerPat))
        return element.replace(binding) as RsPatTupleStruct
    }

    private fun replaceTupleCall(element: RsCallExpr, name: String): RsCallExpr {
        val argumentsText = element.valueArgumentList.text.removePrefix("(").removeSuffix(")")
        val call = factory.createFunctionCall(name, argumentsText)
        val binding = factory.createFunctionCall(element.expr.text, listOf(call))
        return element.replace(binding) as RsCallExpr
    }
}

private class StructVariant(fields: RsBlockFields, factory: RsPsiFactory) : VariantElement(fields, factory) {
    override val typeReferences: List<RsTypeReference> = fields.namedFieldDeclList.mapNotNull { it.typeReference }

    override fun replaceUsage(element: PsiElement, name: String): PsiElement? {
        val replacedUsage = when (val parent = PsiTreeUtil.getParentOfType(
            element,
            RsPatStruct::class.java,
            RsStructLiteral::class.java
        )) {
            is RsPatStruct -> {
                val replacedParent = replaceStructPattern(parent, name)
                replacedParent.patList.firstOrNull()
            }
            is RsStructLiteral -> {
                val replacedParent = replaceStructLiteral(parent, name)
                replacedParent.valueArgumentList.exprList.firstOrNull()
            }
            else -> null
        }
        return replacedUsage?.descendantOfTypeOrSelf<RsReferenceElement>()
    }

    private fun replaceStructPattern(element: RsPatStruct, name: String): RsPatTupleStruct {
        val path = element.path
        val binding = factory.createPatStruct(name, element.patFieldList, element.patRest)
        val newPat = factory.createPatTupleStruct(path.text, listOf(binding))
        return element.replace(newPat) as RsPatTupleStruct
    }

    private fun replaceStructLiteral(element: RsStructLiteral, name: String): RsCallExpr {
        val literal = factory.createStructLiteral(name, element.structLiteralBody.text)
        val binding = factory.createFunctionCall(element.path.text, listOf(literal))
        return element.replace(binding) as RsCallExpr
    }
}

private data class CollectTypeParametersVisitor(
    val parameters: Map<String, RsTypeParameter>,
    val collected: MutableSet<RsTypeParameter>
) : RsRecursiveVisitor() {
    override fun visitTypeReference(ref: RsTypeReference) {
        super.visitTypeReference(ref)
        val type = ref.type as? TyTypeParameter ?: return
        val parameter = parameters[type.name] ?: return
        collected.add(parameter)
        parameter.bounds.forEach { bound ->
            bound.accept(this)
        }
    }
}

private fun gatherTypeParameters(
    references: List<RsTypeReference>,
    parameters: List<RsTypeParameter>
): List<RsTypeParameter> {
    val parameterMap = parameters.filter { it.name != null }.associateBy { it.name!! }
    val collected = mutableSetOf<RsTypeParameter>()
    for (ref in references) {
        ref.accept(CollectTypeParametersVisitor(parameterMap, collected))
    }
    return collected.sortedBy { parameters.indexOf(it) }
}

private data class CollectLifetimesVisitor(
    val parameters: Map<String, RsTypeParameter>,
    val lifetimeMap: Map<String, RsLifetimeParameter>,
    val collected: MutableSet<RsLifetimeParameter>
) : RsRecursiveVisitor() {

    override fun visitTypeReference(ref: RsTypeReference) {
        super.visitTypeReference(ref)
        val type = ref.type as? TyTypeParameter ?: return
        val parameter = parameters[type.name] ?: return
        parameter.bounds.forEach { bound ->
            bound.accept(this)
        }
    }

    override fun visitLifetime(lifetime: RsLifetime) {
        super.visitLifetime(lifetime)
        val parameter = lifetimeMap[lifetime.name] ?: return
        if (parameter !in collected) {
            collected.add(parameter)
            parameter.accept(this)
        }
    }
}

private fun gatherLifetimes(
    references: List<RsTypeReference>,
    lifetimes: List<RsLifetimeParameter>,
    parameters: List<RsTypeParameter>
): List<RsLifetimeParameter> {
    val parameterMap = parameters.filter { it.name != null }.associateBy { it.name!! }
    val lifetimeMap = lifetimes.filter { it.name != null }.associateBy { it.name!! }
    val collected = mutableSetOf<RsLifetimeParameter>()

    for (ref in references) {
        ref.accept(CollectLifetimesVisitor(parameterMap, lifetimeMap, collected))
    }

    return collected.sortedBy { lifetimes.indexOf(it) }
}


private data class HasConstParameterVisitor(val parameter: RsConstParameter) : TypeVisitor {

    override fun visitTy(ty: Ty): Boolean =
        if (ty.hasCtConstParameters) ty.superVisitWith(this) else false

    override fun visitConst(const: Const): Boolean =
        when {
            const is CtConstParameter -> const.parameter == parameter
            const.hasCtConstParameters -> const.superVisitWith(this)
            else -> false
        }
}

private data class HasTypeParameterVisitor(
    val parameters: Map<String, RsTypeParameter>,
    val ref: RsTypeReference
) : TypeVisitor {
    override fun visitTy(ty: Ty): Boolean =
        when {
            ty is TyTypeParameter -> ty.name in parameters
            ty.hasTyTypeParameters -> ty.superVisitWith(this)
            else -> false
        }
}

private data class Parameters(
    val lifetimes: List<RsLifetimeParameter> = emptyList(),
    val typeParameters: List<RsTypeParameter> = emptyList(),
    val constParameters: List<RsConstParameter> = emptyList()
) {
    fun format(): String {
        val all = lifetimes + typeParameters + constParameters
        return if (all.isNotEmpty()) {
            all.joinToString(", ", prefix = "<", postfix = ">") { it.text }
        } else {
            ""
        }
    }
}

private fun filterTypeParameters(
    references: List<RsTypeReference>,
    parameters: RsTypeParameterList?
): Parameters {
    if (parameters == null) return Parameters()
    val typeParameters = gatherTypeParameters(references, parameters.typeParameterList)
    val lifetimes = gatherLifetimes(references, parameters.lifetimeParameterList, typeParameters)
    val constParameters = parameters.constParameterList
        .filter { param -> references.any { matchesConstParameter(it, param) } }
    return Parameters(lifetimes, typeParameters, constParameters)
}

private fun matchesConstParameter(ref: RsTypeReference, parameter: RsConstParameter): Boolean =
    ref.type.visitWith(HasConstParameterVisitor(parameter))

private fun offerStructRename(
    project: Project,
    editor: Editor,
    inserted: RsStructItem,
    additionalElementsToRename: List<PsiElement>
) {
    val range = inserted.identifier?.textRange ?: return
    editor.caretModel.moveToOffset(range.startOffset)

    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
    RsInPlaceVariableIntroducer(inserted, editor, project, "choose struct name", additionalElementsToRename)
        .performInplaceRefactoring(null)
}

private fun createElement(variant: RsEnumVariant, factory: RsPsiFactory): VariantElement {
    val tupleFields = variant.tupleFields
    val blockFields = variant.blockFields

    return when {
        tupleFields != null -> TupleVariant(tupleFields, factory)
        blockFields != null -> StructVariant(blockFields, factory)
        else -> error("unreachable")
    }
}

private fun buildWhereClause(whereClause: RsWhereClause?, parameters: Parameters): String {
    val where = whereClause ?: return ""
    if (where.wherePredList.isEmpty()) return ""

    val parameterMap = parameters.typeParameters.filter { it.name != null }.associateBy { it.name!! }
    val lifetimeMap = parameters.lifetimes.filter { it.name != null }.associateBy { it.name!! }
    val predicates = where.wherePredList.mapNotNull { predicate ->
        val typeRef = predicate.typeReference
        if (typeRef != null && hasTypeParameter(typeRef, parameterMap)) {
            return@mapNotNull predicate.text
        }

        val lifetime = predicate.lifetime
        if (lifetime != null) {
            return@mapNotNull createLifetimePredicate(
                predicate,
                lifetime,
                predicate.lifetimeParamBounds,
                lifetimeMap
            )
        }

        null
    }

    return if (predicates.isNotEmpty()) {
        predicates.joinToString(separator = ",", prefix = " where ")
    } else {
        ""
    }
}

/**
 * Create a predicate if the lifetime is in the map and at least one of its bounds is in the map.
 * Bounds that are not in the map are removed.
 */
private fun createLifetimePredicate(
    predicate: RsWherePred,
    lifetime: RsLifetime,
    lifetimeParamBounds: RsLifetimeParamBounds?,
    lifetimeMap: Map<String, RsLifetimeParameter>
): String? {
    if (lifetime.name !in lifetimeMap) return null
    if (lifetimeParamBounds == null) return predicate.text
    val bounds = lifetimeParamBounds.lifetimeList.filter { it.name in lifetimeMap }
    return if (bounds.isNotEmpty()) {
        "${lifetime.text}: ${bounds.joinToString(" + ") { it.text }}"
    } else {
        null
    }
}

private fun hasTypeParameter(ref: RsTypeReference, map: Map<String, RsTypeParameter>): Boolean =
    HasTypeParameterVisitor(map, ref).visitTy(ref.type)

private fun findTransitiveAttributes(enum: RsEnumItem, supportedAttributes: Set<String>): List<RsOuterAttr> =
    enum.outerAttrList.filter { it.metaItem.name in supportedAttributes }
