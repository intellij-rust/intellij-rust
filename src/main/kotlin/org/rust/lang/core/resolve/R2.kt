package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFieldExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsMethodCallExpr
import org.rust.lang.core.psi.RsStructExprField
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.stripAllRefsIfAny
import org.rust.lang.core.types.type
import org.rust.lang.core.types.types.RustStructType

class ResolveConfig(
    val isCompletion: Boolean
)

private class SimpleVariant(override val name: String, override val element: RsCompositeElement) : Variant

private operator fun RsResolveProcessor.invoke(name: String, e: RsCompositeElement): Boolean {
    return this(SimpleVariant(name, e))
}

private operator fun RsResolveProcessor.invoke(e: RsNamedElement): Boolean {
    val name = e.name ?: return false
    return this(name, e)
}


/// References

fun processResolveVariants(fieldExpr: RsFieldExpr, config: ResolveConfig, processor: RsResolveProcessor): Boolean {
    val receiverType = fieldExpr.expr.type.stripAllRefsIfAny()

    val struct = (receiverType as? RustStructType)?.item
    if (struct != null && processFields(struct, processor)) return true

    if (config.isCompletion) {
        processMethods(fieldExpr.project, receiverType, processor)
    }

    return false
}

fun processResolveVariants(field: RsStructExprField, processor: RsResolveProcessor): Boolean {
    val structOrEnumVariant = field.parentStructExpr.path.reference.resolve() as? RsFieldsOwner ?: return false
    return processFields(structOrEnumVariant, processor)
}

fun processResolveVariants(callExpr: RsMethodCallExpr, processor: RsResolveProcessor): Boolean {
    val receiverType = callExpr.expr.type
    return processMethods(callExpr.project, receiverType, processor)
}

/// Named elements

fun processFields(struct: RsFieldsOwner, processor: RsResolveProcessor): Boolean {
    for (field in struct.namedFields) {
        if (processor(field)) return true
    }

    for ((idx, field) in struct.positionalFields.withIndex()) {
        if (processor(idx.toString(), field)) return true
    }
    return false
}

fun processMethods(project: Project, receiver: RustType, processor: RsResolveProcessor): Boolean {
    val (inherent, nonInherent) = receiver.getMethodsIn(project).partition { it is RsFunction && it.isInherentImpl }
    for (fn in inherent) {
        if (processor(fn)) return true
    }
    val inherentNames = inherent.mapNotNull { it.name }.toHashSet()
    for (fn in nonInherent) {
        if (fn.name in inherentNames) continue
        if (processor(fn)) return true
    }
    return false
}
