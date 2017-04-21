package org.rust.lang.core.types

import com.intellij.openapi.util.Computable
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.ide.utils.recursionGuard
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.types.*

val RsExpr.type: RustType
    get() = CachedValuesManager.getCachedValue(this, CachedValueProvider {
        val type = recursionGuard(this, Computable { RustTypificationEngine.typifyExpr(this) })
            ?: RustUnknownType
        CachedValueProvider.Result.create(type, PsiModificationTracker.MODIFICATION_COUNT)
    })

val RsTypeReference.type: RustType
    get() = recursionGuard(this, Computable { typeReferenceToType(this) })
        ?: RustUnknownType

val RsTypeBearingItemElement.type: RustType
    get() = CachedValuesManager.getCachedValue(this, CachedValueProvider {
        val type = recursionGuard(this, Computable { RustTypificationEngine.typify(this) })
            ?: RustUnknownType
        CachedValueProvider.Result.create(type, PsiModificationTracker.MODIFICATION_COUNT)
    })


private fun typeReferenceToType(ref: RsTypeReference): RustType {
    return when (ref) {
        is RsTupleType -> {
            val single = ref.typeReferenceList.singleOrNull()
            if (single != null && ref.rparen.getPrevNonCommentSibling()?.elementType != COMMA) {
                return typeReferenceToType(single)
            }
            if (ref.typeReferenceList.isEmpty()) {
                return RustUnitType
            }
            RustTupleType(ref.typeReferenceList.map(::typeReferenceToType))
        }

        is RsBaseType -> {
            val path = ref.path ?: return RustUnknownType
            if (path.path == null && !path.isCrateRelative) {
                val primitiveType = RustPrimitiveType.fromTypeName(path.referenceName)
                if (primitiveType != null) return primitiveType
            }
            val target = ref.path?.reference?.resolve() as? RsNamedElement
                ?: return RustUnknownType
            val typeArguments = path.typeArgumentList?.typeReferenceList.orEmpty()
            RustTypificationEngine.typify(target)
                .withTypeArguments(typeArguments.map { it.type })

        }

        is RsRefLikeType -> {
            val base = ref.typeReference ?: return RustUnknownType
            val mutable = ref.isMut
            if (ref.isRef) {
                RustReferenceType(typeReferenceToType(base), mutable)
            } else {
                if (ref.mul != null) { //Raw pointers
                    RustPointerType(typeReferenceToType(base), mutable)
                } else {
                    RustUnknownType
                }
            }
        }

        is RsArrayType -> {
            val componentType = ref.typeReference?.type ?: RustUnknownType
            val size = ref.arraySize
            if (size == null) {
                RustSliceType(componentType)
            } else {
                RustArrayType(componentType, size)
            }
        }

        else -> RustUnknownType
    }
}
