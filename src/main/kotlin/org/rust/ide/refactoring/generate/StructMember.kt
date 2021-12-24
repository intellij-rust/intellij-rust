/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.generate

import org.rust.ide.presentation.PsiRenderingOptions
import org.rust.ide.presentation.TypeSubstitutingPsiRenderer
import org.rust.ide.presentation.renderTypeReference
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsFieldDecl
import org.rust.lang.core.psi.ext.isTupleStruct
import org.rust.lang.core.psi.ext.namedFields
import org.rust.lang.core.psi.ext.positionalFields
import org.rust.lang.core.types.Substitution

data class StructMember(
    val argumentIdentifier: String,
    val fieldIdentifier: String,
    val typeReferenceText: String,
    val field: RsFieldDecl
) {
    val dialogRepresentation: String get() = "$argumentIdentifier: $typeReferenceText"

    companion object {
        fun fromStruct(structItem: RsStructItem, substitution: Substitution): List<StructMember> {
            return if (structItem.isTupleStruct) {
                fromTupleList(structItem.positionalFields, substitution)
            } else {
                fromFieldList(structItem.namedFields, substitution)
            }
        }

        private fun fromTupleList(
            tupleFieldList: List<RsTupleFieldDecl>,
            substitution: Substitution
        ): List<StructMember> {
            return tupleFieldList.mapIndexed { index, tupleField ->
                val typeName = tupleField.typeReference.render(substitution)
                StructMember("field$index", "()", typeName, tupleField)
            }
        }

        private fun fromFieldList(
            fieldDeclList: List<RsNamedFieldDecl>,
            substitution: Substitution
        ): List<StructMember> {
            return fieldDeclList.map {
                StructMember(
                    it.identifier.text ?: "()",
                    it.identifier.text + ":()",
                    it.typeReference?.render(substitution) ?: "()",
                    it
                )
            }
        }
    }
}

private fun RsTypeReference.render(substitution: Substitution): String =
    TypeSubstitutingPsiRenderer(PsiRenderingOptions(shortPaths = false), substitution).renderTypeReference(this)
