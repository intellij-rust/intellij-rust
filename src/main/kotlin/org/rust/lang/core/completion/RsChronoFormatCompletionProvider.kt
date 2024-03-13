/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.STRING_LITERAL
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.psi.ext.patText
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.psiElement
import org.rust.lang.core.types.RsCallable
import org.rust.lang.core.types.ty.TyFunctionDef
import org.rust.lang.core.types.type
import org.rust.lang.core.with


object RsChronoFormatCompletionProvider: RsCompletionProvider() {
    override val elementPattern: ElementPattern<out PsiElement>
        get() = psiElement<PsiElement>().withElementType(STRING_LITERAL).with("isChronoFormatArgument") { psi ->
            val argument = psi.parent as? RsLitExpr ?: return@with false
            val argumentList = argument.parent as? RsValueArgumentList ?: return@with false
            val resolvedFunction = resolveFunction(argumentList.parent)
            if (resolvedFunction?.containingCargoPackage?.name != "chrono") return@with false
            val i = argumentList.exprList.indexOf(argument)
            resolvedFunction.valueParameters.getOrNull(i)?.patText == "fmt"
        }

    private fun resolveFunction(o: PsiElement): RsFunction? {
        return when (o) {
            is RsCallExpr -> ((o.expr.type as? TyFunctionDef)?.def as? RsCallable.Function)?.fn
            is RsMethodCall -> o.reference.resolve() as? RsFunction
            else -> null
        }
    }

    private fun getDateTimeFormatCharactersWithDescriptions(): Map<String, @Nls String> {
        return mapOf(
            Pair("%Y", RsBundle.message("the.full.proleptic.gregorian.year.zero.padded.to.4.digits.2021")),
            Pair("%C", RsBundle.message("the.proleptic.gregorian.year.divided.by.100.zero.padded.to.2.digits.20")),
            Pair("%y", RsBundle.message("the.proleptic.gregorian.year.modulo.100.zero.padded.to.2.digits.01")),
            Pair("%m", RsBundle.message("month.number.01.12.zero.padded.to.2.digits.07")),
            Pair("%b", RsBundle.message("abbreviated.month.name.always.3.letters.jul")),
            Pair("%B", RsBundle.message("full.month.name.july")),
            Pair("%h", RsBundle.message("abbreviated.month.name.always.3.letters.jul")),
            Pair("%d", RsBundle.message("day.number.01.31.zero.padded.to.2.digits")),
            Pair("%e", RsBundle.message("same.as.d.but.space.padded.8")),
            Pair("%a", RsBundle.message("abbreviated.weekday.name.always.3.letters.sun")),
            Pair("%A", RsBundle.message("full.weekday.name.sunday")),
            Pair("%w", RsBundle.message("sunday.0.monday.1.saturday.6")),
            Pair("%u", RsBundle.message("monday.1.tuesday.2.sunday.7.iso.8601")),
            Pair("%U", RsBundle.message("week.number.starting.with.sunday.00.53.zero.padded.to.2.digits")),
            Pair("%W", RsBundle.message("same.as.u.but.week.1.starts.with.the.first.monday.in.that.year.instead")),
            Pair("%G", RsBundle.message("same.as.y.but.uses.the.year.number.in.iso.8601.week.date.2001")),
            Pair("%g", RsBundle.message("same.as.y.but.uses.the.year.number.in.iso.8601.week.date")),
            Pair("%V", RsBundle.message("same.as.u.but.uses.the.week.number.in.iso.8601.week.date.01.53")),
            Pair("%j", RsBundle.message("day.of.the.year.001.366.zero.padded.to.3.digits")),
            Pair("%D", RsBundle.message("month.day.year.format.same.as.m.d.y")),
            Pair("%x", RsBundle.message("locale.s.date.representation.12.31.99")),
            Pair("%F", RsBundle.message("year.month.day.format.iso.8601.same.as.y.m.d")),
            Pair("%v", RsBundle.message("day.month.year.format.same.as.e.b.y")),
            Pair("%H", RsBundle.message("hour.number.00.23.zero.padded.to.2.digits")),
            Pair("%k", RsBundle.message("same.as.h.but.space.padded.same.as.h")),
            Pair("%I", RsBundle.message("hour.number.in.12.hour.clocks.01.12.zero.padded.to.2.digits")),
            Pair("%l", RsBundle.message("same.as.i.but.space.padded.same.as.i")),
            Pair("%P", RsBundle.message("am.or.pm.in.12.hour.clocks2")),
            Pair("%p", RsBundle.message("am.or.pm.in.12.hour.clocks")),
            Pair("%M", RsBundle.message("minute.number.00.59.zero.padded.to.2.digits")),
            Pair("%S", RsBundle.message("second.number.00.60.zero.padded.to.2.digits")),
            Pair("%f", RsBundle.message("the.fractional.seconds.in.nanoseconds.since.last.whole.second.026490000")),
            Pair("%.f", RsBundle.message("similar.to.f.but.left.aligned.these.all.consume.the.leading.dot.026490")),
            Pair("%.3f", RsBundle.message("similar.to.f.but.left.aligned.but.fixed.to.a.length.of.3.026")),
            Pair("%.6f", RsBundle.message("similar.to.f.but.left.aligned.but.fixed.to.a.length.of.6.026490")),
            Pair("%.9f", RsBundle.message("similar.to.f.but.left.aligned.but.fixed.to.a.length.of.9.026490000")),
            Pair("%3f", RsBundle.message("similar.to.3f.but.without.the.leading.dot.026")),
            Pair("%6f", RsBundle.message("similar.to.6f.but.without.the.leading.dot.026490")),
            Pair("%9f", RsBundle.message("similar.to.9f.but.without.the.leading.dot.026490000")),
            Pair("%R", RsBundle.message("hour.minute.format.same.as.h.m")),
            Pair("%T", RsBundle.message("hour.minute.second.format.same.as.h.m.s")),
            Pair("%X", RsBundle.message("locale.s.time.representation.23.13.48")),
            Pair("%r", RsBundle.message("hour.minute.second.format.in.12.hour.clocks.same.as.i.m.s.p")),
            Pair("%Z", RsBundle.message("local.time.zone.name.skips.all.non.whitespace.characters.during.parsing.acst")),
            Pair("%z", RsBundle.message("offset.from.the.local.time.to.utc.with.utc.being.0000")),
            Pair("%:z", RsBundle.message("same.as.z.but.with.a.colon")),
            Pair("%::z", RsBundle.message("offset.from.the.local.time.to.utc.with.seconds.09.30.00")),
            Pair("%:::z", RsBundle.message("offset.from.the.local.time.to.utc.without.minutes.09")),
            Pair("%#z", RsBundle.message("parsing.only.same.as.z.but.allows.minutes.to.be.missing.or.present")),
            Pair("%c", RsBundle.message("locale.s.date.and.time.thu.mar.3.23.05.25.2005")),
            Pair("%+", RsBundle.message("iso.8601.rfc.3339.date.time.format.2001.07.08t00.34.60.026490.09.30")),
            Pair("%s", RsBundle.message("unix.timestamp.the.number.of.seconds.since.1970.01.01.00.00.utc.994518299")),
            Pair("%t", RsBundle.message("literal.tab.t")),
            Pair("%n", RsBundle.message("literal.newline.n")),
            Pair("%%", RsBundle.message("literal.percent.sign")),
        )
    }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position
        val offsetInElement = element.startOffset - parameters.offset
        val r = if (element.text.getOrNull(offsetInElement - 1) == '%') {
            result.withPrefixMatcher("%")
        } else {
            result
        }
        for ((ch, description) in getDateTimeFormatCharactersWithDescriptions()) {
            r.addElement(LookupElementBuilder.create(ch).withTypeText(description, true))
        }
    }
}
