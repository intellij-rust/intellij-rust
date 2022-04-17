/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.annotator.*
import org.rust.ide.colors.RsColor
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.presentation.render
import org.rust.lang.core.FORMAT_ARGS_CAPTURE
import org.rust.lang.core.FeatureAvailability
import org.rust.lang.core.macros.MacroExpansionMode
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.ty.TyNever
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.isUnitTestMode

class RsFormatMacroAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val formatMacro = element as? RsMacroCall ?: return
        if (!formatMacro.existsAfterExpansion) return

        val (formatStr, macroArgs) = getFormatMacroCtx(formatMacro) ?: return

        if (!holder.isBatchMode) {
            highlightParametersOutside(formatStr, holder)
            highlightParametersInside(formatStr, holder)
        }

        val suppressTraitErrors = !isUnitTestMode &&
            (element.project.macroExpansionManager.macroExpansionMode !is MacroExpansionMode.New
                || element.isDoctestInjection)

        val parameters = buildParameters(formatStr)
        val arguments = macroArgs.toList()
        val ctx = FormatContext(parameters, arguments, formatMacro)

        val annotations = checkParameters(ctx) + checkArguments(ctx)
        for (annotation in annotations) {
            if (suppressTraitErrors && annotation.isTraitError) continue

            holder.newAnnotation(HighlightSeverity.ERROR, annotation.error).range(annotation.range).create()
        }
    }
}

private sealed class ParameterLookup {
    data class Named(val name: String) : ParameterLookup()
    data class Positional(val position: Int) : ParameterLookup()
}

private class FormatParameter(
    val lookup: ParameterLookup,
    val block: RsInterpolationBlock,
) {
    val traitType: String? = block.fmtSpecifier?.fmtType?.text
}

@Suppress("unused")
private enum class FormatTraitType(
    private val resolver: (KnownItems) -> RsTraitItem?,
    vararg val names: String
) {
    Display(KnownItems::Display, ""),
    Debug(KnownItems::Debug, "?", "x?", "X?"),
    Octal(KnownItems::Octal, "o"),
    LowerHex(KnownItems::LowerHex, "x"),
    UpperHex(KnownItems::UpperHex, "X"),
    Pointer(KnownItems::Pointer, "p"),
    Binary(KnownItems::Binary, "b"),
    LowerExp(KnownItems::LowerExp, "e"),
    UpperExp(KnownItems::UpperExp, "E");

    fun resolveTrait(knownItems: KnownItems): RsTraitItem? = resolver(knownItems)

    companion object {
        private val nameToTraitMap: Map<String, FormatTraitType> =
            values().flatMap { trait -> trait.names.map { it to trait } }.toMap()

        fun forString(name: String): FormatTraitType? =
            nameToTraitMap[name]
    }
}

private data class FormatContext(
    val parameters: List<FormatParameter>,
    val arguments: List<RsFormatMacroArg>,
    val macro: RsMacroCall
) {
    val namedParameters = parameters.mapNotNull {
        val lookup = it.lookup as? ParameterLookup.Named ?: return@mapNotNull null
        it to lookup
    }.toSet()
    val positionalParameters = parameters.mapNotNull {
        val lookup = it.lookup as? ParameterLookup.Positional ?: return@mapNotNull null
        it to lookup
    }.toSet()

    val namedArguments: Map<String, RsFormatMacroArg> = arguments.mapNotNull { it.name()?.to(it) }.toMap()
}

private data class ErrorAnnotation(
    val range: TextRange,
    @InspectionMessage val error: String,
    val isTraitError: Boolean = false
)

private fun getFormatMacroCtx(formatMacro: RsMacroCall): Pair<RsFormatString, List<RsFormatMacroArg>>? {
//    val macro = formatMacro.path.reference?.resolve() as? RsMacro ?: return null
//    val macroName = macro.name ?: return null

//    val crate = macro.containingCrate ?: return null
    val formatMacroArgs = formatMacro.formatMacroArgument ?: return null

//    if (crate.origin != PackageOrigin.STDLIB || formatMacroArgs === null) return null

    val formatString = formatMacroArgs.formatString ?: return null
    val formatArgs = formatMacroArgs.formatMacroArgList
    return formatString to formatArgs

    /* TODO
    val position = when (macroName) {
        "println",
        "print",
        "eprintln",
        "eprint",
        "format",
        "format_args",
        "format_args_nl" -> 0
        // panic macro handles any literal (even with `{}`) if it's single argument in 2015 and 2018 editions,
        // but starting with edition 2021 the first string literal is always format string
        "panic" -> {
            val edition = formatMacro.containingCrate?.edition ?: CargoWorkspace.Edition.DEFAULT
            if (formatMacroArgs.size < 2 && edition < CargoWorkspace.Edition.EDITION_2021) null else 0
        }
        "write",
        "writeln" -> 1
        else -> null
    } ?: return null
    return Pair(position, formatMacroArgs)*/
}

private fun highlightParametersOutside(formatString: RsFormatString, holder: AnnotationHolder) {
    val key = RsColor.FORMAT_PARAMETER
    val highlightSeverity = if (isUnitTestMode) key.testSeverity else HighlightSeverity.INFORMATION

    for (block in formatString.interpolationBlockList) {
        holder.newSilentAnnotation(highlightSeverity).range(block.textRange).textAttributes(key.textAttributesKey).create()
    }
}

private fun highlightParametersInside(formatString: RsFormatString, holder: AnnotationHolder) {
    fun highlight(range: TextRange?, color: RsColor = RsColor.FORMAT_SPECIFIER) {
        if (range != null) {
            val highlightSeverity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION
            holder.newSilentAnnotation(highlightSeverity)
                .range(range)
                .textAttributes(color.textAttributesKey)
                .create()
        }
    }

    for (block in formatString.interpolationBlockList) {
        val spec = block.fmtSpecifier
        highlight(spec?.let { TextRange(it.startOffset, spec?.fmtType?.endOffset ?: it.endOffset) }, RsColor.FUNCTION)
        highlight(spec?.fmtType?.textRange, RsColor.FUNCTION)
    }
}

private fun buildParameters(formatString: RsFormatString): List<FormatParameter> {
    var implicitPositionCounter = 0

    return formatString.interpolationBlockList.flatMap { block ->
        val parameters = mutableListOf<FormatParameter>()

        val spec = block.fmtSpecifier
        val expr = block.expr

        val lookup = if (expr != null) {
            buildLookup(expr)
        } else {
            ParameterLookup.Positional(implicitPositionCounter++)
        }
        // TODO: handle width, precision and asterisk precision arguments
        val mainParameter = FormatParameter(lookup, block)
        parameters.add(mainParameter)
        parameters
    }
}

private fun buildLookup(expr: RsExpr): ParameterLookup {
    return when (expr) {
        is RsLitExpr -> ParameterLookup.Positional(expr.integerValue!!.toInt())
        is RsPathExpr -> {
            check(expr.path.path == null)
            ParameterLookup.Named(expr.path.text)
        }
        else -> error("TODO: parser error")
    }
}

private fun checkParameters(ctx: FormatContext): List<ErrorAnnotation> {
    val implicitNamedArgsAvailable = FORMAT_ARGS_CAPTURE.availability(ctx.macro) == FeatureAvailability.AVAILABLE
    return ctx.parameters.flatMap {
        checkParameter(it, ctx, implicitNamedArgsAvailable)
    }
}

private fun checkParameter(
    parameter: FormatParameter,
    ctx: FormatContext,
    implicitNamedArgsAvailable: Boolean
): List<ErrorAnnotation> {
    val errors = mutableListOf<ErrorAnnotation>()

    when (val lookup = parameter.lookup) {
        is ParameterLookup.Named -> {
            // TODO: handle name resolution lookup
            if (lookup.name !in ctx.namedArguments && !implicitNamedArgsAvailable) {
                errors.add(ErrorAnnotation(parameter.block.expr!!.textRange, "There is no argument named `${lookup.name}`"))
            }
        }
        is ParameterLookup.Positional -> {
            if (lookup.position >= ctx.arguments.size) {
                val count = when (ctx.arguments.size) {
                    0 -> "no arguments were given"
                    1 -> "there is 1 argument"
                    else -> "there are ${ctx.arguments.size} arguments"
                }
                errors.add(ErrorAnnotation(parameter.block.textRange, "Invalid reference to positional argument ${lookup.position} ($count)"))
            }
        }
    }

    val spec = parameter.block.fmtSpecifier
    if (errors.isEmpty() && spec != null) {
        if (FormatTraitType.forString(spec.text) == null) {
            errors.add(ErrorAnnotation(spec.textRange, "Unknown format trait `${spec.text}`"))
        }
    }

    return errors
}

private fun checkArguments(ctx: FormatContext): List<ErrorAnnotation> {
    return ctx.arguments.flatMap { checkArgument(it, ctx) }
}

private fun checkArgument(argument: RsFormatMacroArg, ctx: FormatContext): List<ErrorAnnotation> {
    val errors = mutableListOf<ErrorAnnotation>()
    val name = argument.name()
    val position = ctx.arguments.indexOf(argument)
    val hasPositionalParameter = ctx.positionalParameters.find { it.second.position == position } != null

    if (name == null) {
        val firstNamed = ctx.arguments.indexOfFirst { it.identifier != null }
        if (firstNamed != -1 && firstNamed < position) {
            errors.add(ErrorAnnotation(argument.textRange, "Positional arguments cannot follow named arguments"))
        } else if (!hasPositionalParameter) {
            errors.add(ErrorAnnotation(argument.textRange, "Argument never used"))
        }
    } else if (name !in ctx.namedParameters.map { it.second.name } && !hasPositionalParameter) {
        errors.add(ErrorAnnotation(argument.textRange, "Named argument never used"))
    }

    if (errors.isEmpty()) {
        val parameters = findParameters(argument, position, ctx)
        check(parameters.isNotEmpty()) // should be caught by the checks above
        for (parameter in parameters) {
//            val error = when (parameter) {
//                is FormatParameter.Value -> checkParameterTraitMatch(argument, parameter)
//                is FormatParameter.Specifier -> checkSpecifierType(argument, parameter)
//            }
            val error = checkParameterTraitMatch(argument, parameter)
            error?.let {
                errors.add(it)
            }
        }
    }

    return errors
}

private fun findParameters(argument: RsFormatMacroArg, position: Int, ctx: FormatContext): List<FormatParameter> {
    val name = argument.name()
    val positional = ctx.positionalParameters.filter { it.second.position == position }.map { it.first }.toList()
    return if (name == null) {
        positional
    } else {
        positional + ctx.namedParameters.filter { it.second.name == name }.map { it.first }.toList()
    }
}

private val IGNORED_FORMAT_TYPES = setOf(TyUnknown, TyNever)

private fun checkParameterTraitMatch(argument: RsFormatMacroArg, parameter: FormatParameter): ErrorAnnotation? {
    val requiredTrait = parameter.traitType?.let { FormatTraitType.forString(it) }?.resolveTrait(argument.knownItems)
        ?: return null

    val expr = argument.expr
    if (!IGNORED_FORMAT_TYPES.any { expr.type.containsTyOfClass(it::class.java) } &&
        !expr.implLookup.canSelectWithDeref(TraitRef(expr.type, requiredTrait.withSubst()))) {
        return ErrorAnnotation(
            argument.textRange,
            "`${expr.type.render()}` doesn't implement `${requiredTrait.name}`" +
                " (required by ${parameter.traitType})",
            isTraitError = true
        )
    }
    return null
}

// i32 is allowed because of integers without a specific type
private val ALLOWED_SPECIFIERS_TYPES = setOf(TyInteger.USize.INSTANCE, TyInteger.I32.INSTANCE)

//private fun checkSpecifierType(argument: RsFormatMacroArg, parameter: FormatParameter.Specifier): ErrorAnnotation? {
//    val expr = argument.expr
//    val type = expr.type.stripReferences()
//    if (type !in ALLOWED_SPECIFIERS_TYPES) {
//        return ErrorAnnotation(argument.textRange, "${parameter.specifier.capitalize()} specifier must be of type `usize`")
//    }
//    return null
//}

private fun RsFormatMacroArg.name(): String? = this.identifier?.text
