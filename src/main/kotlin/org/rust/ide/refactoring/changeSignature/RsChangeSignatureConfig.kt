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
import org.rust.lang.core.macros.setContext
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnit

/**
 * This class just holds [config].
 * It is required by [com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase].
 */
class RsSignatureChangeInfo(
    val config: RsChangeFunctionSignatureConfig,
    val changeSignature: Boolean
) : ChangeInfo {
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
 * This type is needed to distinguish between empty, invalid and valid type references or expressions entered
 * in the dialog.
 */
sealed class ParameterProperty<T: RsElement> {
    class Empty<T: RsElement> : ParameterProperty<T>()
    class Invalid<T: RsElement>(override val text: String) : ParameterProperty<T>()
    class Valid<T: RsElement>(override val item: T) : ParameterProperty<T>() {
        override val text: String
            get() = item.text
    }

    open val text: String = ""
    open val item: T? = null

    companion object {
        fun <T: RsElement> fromItem(item: T?): ParameterProperty<T> = when (item) {
            null -> Empty()
            else -> Valid(item)
        }
        fun <T: RsElement> fromText(item: T?, text: String): ParameterProperty<T> = when {
            text.isBlank() -> Empty()
            item == null -> Invalid(text)
            else -> Valid(item)
        }
    }
}

class Parameter(
    val factory: RsPsiFactory,
    var patText: String,
    var type: ParameterProperty<RsTypeReference>,
    val index: Int = NEW_PARAMETER,
    var defaultValue: ParameterProperty<RsExpr> = ParameterProperty.Empty()
) {
    val typeReference: RsTypeReference
        get() = parseTypeReference() ?: factory.createType("()")

    fun parseTypeReference(): RsTypeReference? = type.item
    private fun parsePat(): RsPat? = factory.tryCreatePat(patText)

    fun hasValidPattern(): Boolean {
        if (parsePat() == null) {
            return false
        }

        if (factory.tryCreateValueParameter(patText, parseTypeReference() ?: factory.createType("()")) == null) {
            return false
        }

        return true
    }

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
    var isUnsafe: Boolean = false,
    val additionalTypesToImport: MutableList<Ty> = mutableListOf()
) : RsFunctionSignatureConfig(function) {
    override fun typeParameters(): List<RsTypeParameter> = function.typeParameters

    val returnTypeReference: RsTypeReference
        get() = returnTypeDisplay ?: RsPsiFactory(function.project).createType("()")

    val allowsVisibilityChange: Boolean
        get() = !(function.owner is RsAbstractableOwner.Trait || function.owner.isTraitImpl)

    val parameters: MutableList<Parameter> = originalParameters.toMutableList()

    private val originalName: String = function.name.orEmpty()

    val returnType: Ty
        get() = returnTypeDisplay?.rawType ?: TyUnit.INSTANCE

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

    fun createChangeInfo(changeSignature: Boolean = true): ChangeInfo = RsSignatureChangeInfo(this, changeSignature)
    fun nameChanged(): Boolean = name != originalName
    fun parameterSetOrOrderChanged(): Boolean = parameters.map { it.index } != originalParameters.indices.toList()

    companion object {
        fun create(function: RsFunction): RsChangeFunctionSignatureConfig {
            val factory = RsPsiFactory(function.project)
            val parameters = function.rawValueParameters.mapIndexed { index, parameter ->
                val patText = parameter.pat?.text ?: "_"
                // The element has to be copied, otherwise suggested refactoring API
                // would revert the PSI item to its previous state
                val parameterCopy = (parameter.typeReference?.copy() as? RsTypeReference)
                    ?.also {
                        val context = parameter.typeReference?.parent?.context as? RsElement
                        context?.let { it1 -> it.setContext(it1) }
                    }
                val type = ParameterProperty.fromItem(parameterCopy)
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
