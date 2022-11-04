/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.format

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import org.intellij.lang.annotations.Language
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.colors.RsColor
import org.rust.ide.presentation.render
import org.rust.lang.core.CompilerFeature.Companion.FORMAT_ARGS_CAPTURE
import org.rust.lang.core.FeatureAvailability
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.psi.ext.withSubst
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.ty.TyNever
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.ty.stripReferences
import org.rust.lang.core.types.type
import org.rust.lang.utils.parseRustStringCharacters
import org.rust.openapiext.isUnitTestMode
import org.rust.stdext.capitalized

data class ParameterMatchInfo(val range: TextRange, val text: String)

sealed class ParameterLookup {
    data class Named(val name: String) : ParameterLookup()
    data class Positional(val position: Int) : ParameterLookup()
}

sealed class FormatParameter(val matchInfo: ParameterMatchInfo, val lookup: ParameterLookup) {
    override fun toString(): String {
        return this.matchInfo.text
    }

    val range: TextRange = matchInfo.range

    // normal parameter which will be formatted
    class Value(
        matchInfo: ParameterMatchInfo,
        lookup: ParameterLookup,
        val typeStr: String,
        val typeRange: TextRange
    ) : FormatParameter(matchInfo, lookup) {
        val type: FormatTraitType? = FormatTraitType.forString(typeStr)
    }

    // width or precision formatting specifier
    class Specifier(matchInfo: ParameterMatchInfo, lookup: ParameterLookup, val specifier: String)
        : FormatParameter(matchInfo, lookup)
}

@Suppress("unused")
enum class FormatTraitType(
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

data class FormatContext(
    val parameters: List<FormatParameter>,
    val arguments: List<RsFormatMacroArg>,
    val macro: RsMacroCall
) {
    val namedParameters = parameters.mapNotNull {
        val lookup = it.lookup as? ParameterLookup.Named ?: return@mapNotNull null
        Pair(it, lookup)
    }.toSet()
    val positionalParameters = parameters.mapNotNull {
        val lookup = it.lookup as? ParameterLookup.Positional ?: return@mapNotNull null
        Pair(it, lookup)
    }.toSet()

    val namedArguments: Map<String, RsFormatMacroArg> = arguments.mapNotNull { it.name()?.to(it) }.toMap()
}

data class ParsedParameter(
    val completeMatch: MatchResult,
    val innerContentMatch: MatchResult? = null
) {
    val innerContent = completeMatch.groups[2]
    val range: IntRange = completeMatch.range
}

class ParseContext(private val sourceMap: IntArray, val offset: Int, val parameters: List<ParsedParameter>) {
    fun toSourceRange(range: IntRange, additionalOffset: Int = 0): TextRange =
        TextRange(sourceMap[range.first + additionalOffset], sourceMap[range.last + additionalOffset] + 1)
            .shiftRight(offset)
}

data class ErrorAnnotation(
    val range: TextRange,
    @InspectionMessage val error: String,
    val isTraitError: Boolean = false
)

private val formatParser = Regex("""\{\{|}}|(\{([^}]*)}?)|(})""")

@Language("Regexp")
private const val ARGUMENT = """([a-zA-Z_][\w+]*|\d+)"""
private val formatParameterParser = Regex("""(?x) # enable comments
^(?<id>$ARGUMENT)?
(:
    (.?[\^<>])?[+\-]?\#?
    0?(?!\$) # negative lookahead to parse 0$ as width and 00$ as zero padding followed by width
    (?<width>$ARGUMENT\$|\d+)?
    (\.(?<precision>$ARGUMENT\$|\d+|\*)?)? # specifying no precision after a dot is allowed
    (?<type>\w?\??)?
)?\s*""")

fun parseParameters(formatStr: RsLitExpr): ParseContext? {
    val literalKind = (formatStr.kind as? RsLiteralKind.String) ?: return null
    if (literalKind.node.elementType in RS_BYTE_STRING_LITERALS) return null

    val rawTextRange = literalKind.offsets.value ?: return null
    val text = literalKind.rawValue ?: return null
    val (unescapedText, sourceMap) = if (literalKind.node.elementType == RsElementTypes.RAW_STRING_LITERAL) {
        val map = text.indices.toList().toIntArray()
        text to map
    } else {
        val (parsedText, map, _) = parseRustStringCharacters(text)
        parsedText.toString() to map
    }

    val arguments = formatParser.findAll(unescapedText)
    val parsed = arguments.map { arg ->
        if (arg.groups[1] != null) {
            val innerContent = arg.groups[2] ?: error(" should not be null because can match empty string")
            val innerContentMatch = formatParameterParser.find(innerContent.value)
                ?: error(" should be not null because can match empty string")

            ParsedParameter(arg, innerContentMatch)
        } else {
            ParsedParameter(arg)
        }
    }.toList()

    return ParseContext(sourceMap, formatStr.startOffset + rawTextRange.startOffset, parsed)
}

fun checkSyntaxErrors(ctx: ParseContext): List<ErrorAnnotation> {
    val errors = mutableListOf<ErrorAnnotation>()

    for (parameter in ctx.parameters) {
        val completeMatch = parameter.completeMatch
        val range = ctx.toSourceRange(parameter.range)

        if (completeMatch.groups[3] != null) {
            errors.add(ErrorAnnotation(range, "Invalid format string: unmatched '}'"))
        }

        if (parameter.innerContent != null && parameter.innerContentMatch != null) {
            val innerContent = parameter.innerContent
            val content = completeMatch.groups[1]
            if (content != null && !content.value.endsWith("}")) {
                val possibleEnd = parameter.innerContentMatch.value.length - 1
                errors.add(ErrorAnnotation(
                    ctx.toSourceRange(possibleEnd..possibleEnd, innerContent.range.first),
                    "Invalid format string: } expected.\nIf you intended to print `{` symbol, you can escape it using `{{`"
                ))
                continue
            }

            val validParsedEnd = parameter.innerContentMatch.range.last + 1
            if (validParsedEnd != innerContent.value.length) {
                errors.add(ErrorAnnotation(
                    ctx.toSourceRange(validParsedEnd until innerContent.value.length, innerContent.range.first),
                    "Invalid format string"
                ))
            }
        }
    }
    return errors
}

fun highlightParametersOutside(ctx: ParseContext, holder: AnnotationHolder) {
    val key = RsColor.FORMAT_PARAMETER
    val highlightSeverity = if (isUnitTestMode) key.testSeverity else HighlightSeverity.INFORMATION

    for (parameter in ctx.parameters) {
        holder.newSilentAnnotation(highlightSeverity).range(ctx.toSourceRange(parameter.range)).textAttributes(key.textAttributesKey).create()
    }
}

fun highlightParametersInside(ctx: ParseContext, holder: AnnotationHolder) {
    fun highlight(range: IntRange?, offset: Int, color: RsColor = RsColor.FORMAT_SPECIFIER) {
        if (range != null && !range.isEmpty()) {
            val highlightSeverity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION
            holder.newSilentAnnotation(highlightSeverity)
                .range(ctx.toSourceRange(range, offset))
                .textAttributes(color.textAttributesKey)
                .create()
        }
    }

    for (parameter in ctx.parameters) {
        val match = parameter.innerContentMatch
        match?.let {
            val offset = parameter.innerContent?.range?.first ?: return@let

            highlight(it.groups["id"]?.range, offset)
            highlight(it.groups["width"]?.range, offset)
            highlight(it.groups["precision"]?.range, offset)
            highlight(it.groups["type"]?.range, offset, RsColor.FUNCTION)
        }
    }
}

private fun buildLookup(value: String): ParameterLookup {
    val identifier = value.toIntOrNull()
    return if (identifier == null) {
        ParameterLookup.Named(value)
    } else {
        ParameterLookup.Positional(identifier)
    }
}

fun buildParameters(ctx: ParseContext): List<FormatParameter> {
    val ignored = setOf("{{", "}}")
    var implicitPositionCounter = 0

    return ctx.parameters.flatMap { param ->
        val parameters = mutableListOf<FormatParameter>()

        val innerRange = param.innerContent?.range ?: return@flatMap parameters
        val match = param.innerContentMatch ?: return@flatMap parameters

        val id = match.groups["id"]
        val type = match.groups["type"]
        val precision = match.groups["precision"]
        val isPrecisionAsterisk = precision != null && precision.value == "*"
        var isPrecisionValueFirst = false

        if (match.value in ignored) return@flatMap parameters

        val typeStr = type?.value ?: ""
        val typeRange = type?.range ?: IntRange(0, 0)

        val mainParameter = if (id != null) {
            val matchInfo = ParameterMatchInfo(ctx.toSourceRange(id.range, innerRange.first), param.completeMatch.value)
            isPrecisionValueFirst = true
            FormatParameter.Value(matchInfo, buildLookup(id.value), typeStr, ctx.toSourceRange(typeRange, innerRange.first))
        } else {
            val matchInfo = ParameterMatchInfo(ctx.toSourceRange(param.range), param.completeMatch.value)

            if (isPrecisionAsterisk) {
                FormatParameter.Specifier(matchInfo, ParameterLookup.Positional(implicitPositionCounter++), "precision")
            } else {
                FormatParameter.Value(matchInfo, ParameterLookup.Positional(implicitPositionCounter++), typeStr, ctx.toSourceRange(typeRange, innerRange.first))
            }
        }

        parameters.add(mainParameter)

        val specifiers = listOf("width", "precision")
        for (specifier in specifiers) {
            val group = match.groups[specifier] ?: continue
            val text = group.value
            if (!text.endsWith("$")) continue
            parameters.add(FormatParameter.Specifier(
                ParameterMatchInfo(ctx.toSourceRange(group.range, innerRange.first), text),
                buildLookup(text.trimEnd('$')),
                specifier
            ))
        }

        if (isPrecisionAsterisk) {
            if (isPrecisionValueFirst) {
                parameters.add(FormatParameter.Specifier(
                    ParameterMatchInfo(ctx.toSourceRange(precision!!.range, innerRange.first), precision.value),
                    ParameterLookup.Positional(implicitPositionCounter++),
                    "precision"
                ))
            } else {
                val matchInfo = ParameterMatchInfo(ctx.toSourceRange(param.range), param.completeMatch.value)
                parameters.add(FormatParameter.Value(
                    matchInfo, ParameterLookup.Positional(implicitPositionCounter++),
                    typeStr, ctx.toSourceRange(typeRange, innerRange.first))
                )
            }
        }

        parameters
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
            if (lookup.name !in ctx.namedArguments && !implicitNamedArgsAvailable) {
                errors.add(ErrorAnnotation(parameter.range, "There is no argument named `${lookup.name}`"))
            }
        }
        is ParameterLookup.Positional -> {
            if (lookup.position >= ctx.arguments.size) {
                val count = when (ctx.arguments.size) {
                    0 -> "no arguments were given"
                    1 -> "there is 1 argument"
                    else -> "there are ${ctx.arguments.size} arguments"
                }
                errors.add(ErrorAnnotation(parameter.range, "Invalid reference to positional argument ${lookup.position} ($count)"))
            }
        }
    }

    if (errors.isEmpty() && parameter is FormatParameter.Value) {
        if (parameter.type == null) {
            errors.add(ErrorAnnotation(parameter.typeRange, "Unknown format trait `${parameter.typeStr}`"))
        }
    }

    return errors
}

fun checkParameters(ctx: FormatContext): List<ErrorAnnotation> {
    val implicitNamedArgsAvailable = FORMAT_ARGS_CAPTURE.availability(ctx.macro) == FeatureAvailability.AVAILABLE

    val errors = mutableListOf<ErrorAnnotation>()
    for (parameter in ctx.parameters) {
        for (error in checkParameter(parameter, ctx, implicitNamedArgsAvailable)) {
            errors.add(error)
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

private fun checkParameterTraitMatch(argument: RsFormatMacroArg, parameter: FormatParameter.Value): ErrorAnnotation? {
    val requiredTrait = parameter.type?.resolveTrait(argument.knownItems) ?: return null

    val expr = argument.expr
    if (!IGNORED_FORMAT_TYPES.any { expr.type.containsTyOfClass(it::class.java) } &&
        !expr.implLookup.canSelectWithDeref(TraitRef(expr.type, requiredTrait.withSubst()))) {
        return ErrorAnnotation(
            argument.textRange,
            "`${expr.type.render()}` doesn't implement `${requiredTrait.name}`" +
                " (required by ${parameter.matchInfo.text})",
            isTraitError = true
        )
    }
    return null
}

// i32 is allowed because of integers without a specific type
private val ALLOWED_SPECIFIERS_TYPES = setOf(TyInteger.USize.INSTANCE, TyInteger.I32.INSTANCE)

private fun checkSpecifierType(argument: RsFormatMacroArg, parameter: FormatParameter.Specifier): ErrorAnnotation? {
    val expr = argument.expr
    val type = expr.type.stripReferences()
    if (type !in ALLOWED_SPECIFIERS_TYPES) {
        return ErrorAnnotation(argument.textRange, "${parameter.specifier.capitalized()} specifier must be of type `usize`")
    }
    return null
}

private fun checkArgument(argument: RsFormatMacroArg, ctx: FormatContext): List<ErrorAnnotation> {
    val errors = mutableListOf<ErrorAnnotation>()
    val name = argument.name()
    val position = ctx.arguments.indexOf(argument)
    val hasPositionalParameter = ctx.positionalParameters.find { it.second.position == position } != null

    if (name == null) {
        val firstNamed = ctx.arguments.indexOfFirst { it.eq != null }
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
            val error = when (parameter) {
                is FormatParameter.Value -> checkParameterTraitMatch(argument, parameter)
                is FormatParameter.Specifier -> checkSpecifierType(argument, parameter)
            }
            error?.let {
                errors.add(it)
            }
        }
    }

    return errors
}

fun checkArguments(ctx: FormatContext): List<ErrorAnnotation> {
    val errors = mutableListOf<ErrorAnnotation>()
    for (arg in ctx.arguments) {
        for (error in checkArgument(arg, ctx)) {
            errors.add(error)
        }
    }
    return errors
}

fun getFormatMacroCtx(formatMacro: RsMacroCall): Pair<Int, List<RsFormatMacroArg>>? {
    val macro = formatMacro.path.reference?.resolve() as? RsMacro ?: return null
    val macroName = macro.name ?: return null

    val crate = macro.containingCrate
    val formatMacroArgs = formatMacro.formatMacroArgument?.formatMacroArgList

    if (crate.origin != PackageOrigin.STDLIB || formatMacroArgs === null) return null

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
            val edition = formatMacro.containingCrate.edition
            if (formatMacroArgs.size < 2 && edition < CargoWorkspace.Edition.EDITION_2021) null else 0
        }
        "write",
        "writeln" -> 1
        else -> null
    } ?: return null
    return Pair(position, formatMacroArgs)
}

fun RsFormatMacroArg.name(): String? =
    if (eq != null) node.findChildByType(RS_IDENTIFIER_TOKENS)?.text else null
