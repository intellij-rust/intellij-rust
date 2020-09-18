/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.infer.hasReEarlyBounds
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type

class UnElideLifetimesIntention : RsElementBaseIntentionAction<RsFunction>() {
    override fun getText() = "Un-elide lifetimes"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsFunction? {
        if (element is RsDocCommentImpl) return null
        val fn = element.ancestorOrSelf<RsFunction>(stopAt = RsBlock::class.java) ?: return null

        val ctx = getLifetimeContext(fn)
        val outputLifetimes = ctx.output?.lifetimes
        if (outputLifetimes != null) {
            if (outputLifetimes.any { it != null } || outputLifetimes.size > 1) return null
        }

        val refArgs = ctx.inputs + listOfNotNull(ctx.self)
        if (refArgs.isEmpty() || refArgs.any { it.lifetimes.any { lifetime -> lifetime != null } }) return null

        return fn
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsFunction) {
        val (inputs, output, self) = getLifetimeContext(ctx)

        val addedLifetimes = mutableListOf<String>()
        val generator = nameGenerator.iterator()
        (listOfNotNull(self) + inputs).forEach { ref ->
            val names = ref.lifetimes.map { generator.next() }
            addLifetimeParameter(ref, names)
            addedLifetimes.addAll(names)
        }

        val genericParams = RsPsiFactory(project).createTypeParameterList(
            addedLifetimes + ctx.typeParameters.map { it.text }
        )
        ctx.typeParameterList?.replace(genericParams) ?: ctx.addAfter(genericParams, ctx.identifier)

        // return type
        if (output == null) return

        if (self != null || inputs.size == 1) {
            addLifetimeParameter(output, listOf(addedLifetimes.first()))
        } else {
            val unknownLifetime = "'_"
            val element = addLifetimeParameter(output, listOf(unknownLifetime))
            element.accept(object : RsRecursiveVisitor() {
                override fun visitLifetime(o: RsLifetime) {
                    if (o.quoteIdentifier.text == unknownLifetime) {
                        val start = o.startOffset + 1
                        editor.caretModel.moveToOffset(start)
                        editor.selectionModel.setSelection(start, o.endOffset)
                    }
                }
            })
        }
    }

    private val nameGenerator = generateSequence(0) { it + 1 }.map {
        val abcSize = 'z' - 'a' + 1
        val letter = 'a' + it % abcSize
        val index = it / abcSize
        return@map if (index == 0) "'$letter" else "'$letter$index"
    }
}

private sealed class PotentialLifetimeRef(val element: RsElement) {
    data class Self(val self: RsSelfParameter) : PotentialLifetimeRef(self)
    data class RefLike(val ref: RsRefLikeType) : PotentialLifetimeRef(ref)
    data class BaseType(val baseType: RsBaseType, val type: Ty) : PotentialLifetimeRef(baseType) {
        val typeLifetimes: List<Region>
            get() = when (val type = baseType.type) {
                is TyAdt -> type.regionArguments.filter { it.hasReEarlyBounds }
                else -> emptyList()
            }
    }

    val lifetimes: List<RsLifetime?>
        get() = when (this) {
            is Self -> listOf(self.lifetime)
            is RefLike -> listOf(ref.lifetime)
            is BaseType -> {
                val lifetimes = typeLifetimes
                val actualLifetimes = baseType.path?.typeArgumentList?.lifetimeList
                lifetimes.indices.map { actualLifetimes?.getOrNull(it) }
            }
        }
}

private fun isPotentialLifetimeAdt(ref: RsTypeReference): Boolean {
    return when (val type = ref.type) {
        is TyAdt -> type.regionArguments.all { it.hasReEarlyBounds }
        else -> false
    }
}

private fun parsePotentialLifetimeType(ref: RsTypeReference): PotentialLifetimeRef? {
    return when {
        ref is RsRefLikeType -> PotentialLifetimeRef.RefLike(ref)
        ref is RsBaseType && isPotentialLifetimeAdt(ref) -> PotentialLifetimeRef.BaseType(ref, ref.type)
        else -> null
    }
}

private data class LifetimeContext(
    val inputs: List<PotentialLifetimeRef>,
    val output: PotentialLifetimeRef?,
    val self: PotentialLifetimeRef?
)

private fun getLifetimeContext(fn: RsFunction): LifetimeContext {
    val inputArgs = fn.valueParameters.mapNotNull { elem -> elem.typeReference?.let { parsePotentialLifetimeType(it) } }
    val retType = fn.retType?.typeReference?.let { parsePotentialLifetimeType(it) }

    return LifetimeContext(inputArgs, retType, fn.selfParameter?.let { PotentialLifetimeRef.Self(it) })
}

private fun addLifetimeParameter(ref: PotentialLifetimeRef, names: List<String>): PsiElement {
    val factory = RsPsiFactory(ref.element.project)
    return when (ref) {
        is PotentialLifetimeRef.Self -> {
            val elem = ref.element
            elem.replace(factory.createMethodParam(elem.text.replaceFirst("&", "&${names[0]} ")))
        }
        is PotentialLifetimeRef.RefLike -> {
            val elem = ref.element
            val typeRef = factory.createType(elem.text.replaceFirst("&", "&${names[0]} "))
            elem.replace(typeRef)
        }
        is PotentialLifetimeRef.BaseType -> {
            val elem = ref.baseType
            val typeList = names.toMutableList()

            val typeArguments = elem.path?.typeArgumentList
            if (typeArguments != null) {
                typeList += typeArguments.typeReferenceList.map { it.text }
                typeList += typeArguments.assocTypeBindingList.map { it.text }
            }

            val baseTypeName = elem.name
            val types = factory.createTypeParameterList(typeList)
            val replacement = factory.createType("$baseTypeName${types.text}")
            elem.replace(replacement)
        }
    }
}
