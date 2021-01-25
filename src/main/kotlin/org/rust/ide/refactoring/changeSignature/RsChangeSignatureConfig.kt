/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ParameterInfo
import com.intellij.refactoring.changeSignature.ParameterInfo.NEW_PARAMETER
import org.rust.ide.refactoring.RsFunctionSignatureConfig
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type

/**
 * This class just holds [config].
 * It is required by [com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase].
 */
class RsSignatureChangeInfo(val config: RsChangeFunctionSignatureConfig) : ChangeInfo {
    override fun getNewParameters(): Array<ParameterInfo> = arrayOf()
    override fun isParameterSetOrOrderChanged(): Boolean = config.parameterSetOrOrderChanged()
    override fun isParameterTypesChanged(): Boolean = false
    override fun isParameterNamesChanged(): Boolean = false
    override fun isGenerateDelegate(): Boolean = false

    override fun isReturnTypeChanged(): Boolean = config.returnTypeDisplay?.text == config.function.retType?.typeReference?.text

    override fun getNewName(): String = config.name
    override fun isNameChanged(): Boolean = config.nameChanged()

    override fun getMethod(): PsiElement = config.function
    override fun getLanguage(): Language = RsLanguage
}

/**
 * This type is needed to distinguish from empty and invalid type references entered in the dialog.
 */
sealed class ParameterType {
    object Empty: ParameterType()
    class Invalid(override val text: String): ParameterType()
    class Valid(val typeReference: RsTypeReference): ParameterType() {
        override val text: String = typeReference.text
    }

    open val text: String = ""

    companion object {
        fun fromTypeReference(typeReference: RsTypeReference?): ParameterType {
            if (typeReference == null) return Empty
            return Valid(typeReference)
        }
        fun fromText(typeReference: RsTypeReference?, text: String): ParameterType = when {
            text.isBlank() -> Empty
            typeReference == null -> Invalid(text)
            else -> Valid(typeReference)
        }
    }
}

/**
 * This type needs to be comparable by identity, not value.
 */
class Parameter(
    val factory: RsPsiFactory,
    var patText: String,
    var type: ParameterType,
    val index: Int = NEW_PARAMETER
) {
    val typeReference: RsTypeReference
        get() = parseTypeReference() ?: factory.createType("()")

    fun parseTypeReference(): RsTypeReference? = (type as? ParameterType.Valid)?.typeReference
    fun parsePat(): RsPat? = factory.tryCreatePat(patText)

    val pat: RsPat
        get() = parsePat() ?: factory.createPat("_")
}

/**
 * This class holds information about function's properties (name, return type, parameters, etc.).
 * It is designed to be changed (mutably) in the Change Signature dialog.
 *
 * After the dialog finishes, the refactoring will compare the state of the original function with the modified config
 * and perform the necessary adjustments.
 */
class RsChangeFunctionSignatureConfig private constructor(
    function: RsFunction,
    var name: String,
    val originalParameters: List<Parameter>,
    var returnTypeDisplay: RsTypeReference?,
    var visibility: RsVis? = null,
    var isAsync: Boolean = false,
    var isUnsafe: Boolean = false
) : RsFunctionSignatureConfig(function) {
    override fun typeParameters(): List<RsTypeParameter> = function.typeParameters

    val returnTypeReference: RsTypeReference
        get() = returnTypeDisplay ?: RsPsiFactory(function.project).createType("()")

    val allowsVisibilityChange: Boolean
        get() = !(function.owner is RsAbstractableOwner.Trait || function.owner.isTraitImpl)

    val parameters: MutableList<Parameter> = originalParameters.toMutableList()

    private val originalName: String = function.name.orEmpty()

    val returnType: Ty
        get() = returnTypeDisplay?.type ?: TyUnit

    private val parametersText: String
        get() {
            val selfText = listOfNotNull(function.selfParameter?.text)
            val parametersText = parameters.map { "${it.pat.text}: ${it.typeReference.text}" }
            return (selfText + parametersText).joinToString(", ")
        }

    fun signature(): String = buildString {
        visibility?.let { append("${it.text} ") }

        if (isAsync) {
            append("async ")
        }
        if (isUnsafe) {
            append("unsafe ")
        }
        append("fn $name$typeParametersText($parametersText)")
        if (returnType !is TyUnit) {
            append(" -> ${returnTypeReference.text}")
        }
        append(whereClausesText)
    }

    fun createChangeInfo(): ChangeInfo = RsSignatureChangeInfo(this)
    fun nameChanged(): Boolean = name != originalName
    fun parameterSetOrOrderChanged(): Boolean = parameters.map { it.index } != originalParameters.indices.toList()

    companion object {
        fun create(function: RsFunction): RsChangeFunctionSignatureConfig {
            val factory = RsPsiFactory(function.project)
            val parameters = function.valueParameters.mapIndexed { index, parameter ->
                val patText = parameter.pat?.text ?: "_"
                val type = ParameterType.fromTypeReference(parameter.typeReference)
                Parameter(factory, patText, type, index)
            }
            return RsChangeFunctionSignatureConfig(
                function,
                function.name.orEmpty(),
                parameters,
                function.retType?.typeReference,
                function.vis,
                function.isAsync,
                function.isUnsafe
            )
        }
    }
}
