/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.utils.PsiInsertionPlace
import org.rust.ide.utils.checkMatch.Pattern
import org.rust.ide.utils.import.RsImportHelper.importTypeReferencesFromTy
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.type

open class AddRemainingArmsFix(
    match: RsMatchExpr,
    @SafeFieldForPreview
    private val patterns: List<Pattern>,
) : RsQuickFixBase<RsMatchExpr>(match) {
    override fun getFamilyName(): String = NAME
    override fun getText(): String = familyName

    override fun invoke(project: Project, editor: Editor?, element: RsMatchExpr) {
        val expr = element.expr ?: return
        val place = findArmsInsertionPlaceIn(element) ?: return
        invoke(project, element, expr, place)
    }

    fun invoke(project: Project, match: RsMatchExpr, expr: RsExpr, place: ArmsInsertionPlace) {
        val rsPsiFactory = RsPsiFactory(project)
        val newArms = createNewArms(rsPsiFactory, match)
        when (place) {
            is ArmsInsertionPlace.AsNewBody -> {
                val newMatchBody = rsPsiFactory.createMatchBody(emptyList())
                for (arm in newArms) {
                    newMatchBody.addBefore(arm, newMatchBody.rbrace)
                }
                place.placeForBody.insert(newMatchBody)
            }
            is ArmsInsertionPlace.ToExistingBody -> {
                place.placeForComma?.insert(rsPsiFactory.createComma())
                place.placeForArms.insertMultiple(newArms)
            }
        }

        importTypeReferencesFromTy(match, expr.type)
    }

    open fun createNewArms(psiFactory: RsPsiFactory, context: RsElement): List<RsMatchArm> =
        psiFactory.createMatchBody(patterns, context).matchArmList

    sealed class ArmsInsertionPlace {
        class ToExistingBody(val placeForComma: PsiInsertionPlace?, val placeForArms: PsiInsertionPlace) : ArmsInsertionPlace()
        class AsNewBody(val placeForBody: PsiInsertionPlace) : ArmsInsertionPlace()
    }

    companion object {
        @IntentionName
        @IntentionFamilyName
        val NAME = RsBundle.message("intention.name.add.remaining.patterns")

        fun findArmsInsertionPlaceIn(match: RsMatchExpr): ArmsInsertionPlace? {
            val expr = match.expr ?: return null
            return when (val body = match.matchBody) {
                null -> ArmsInsertionPlace.AsNewBody(PsiInsertionPlace.after(expr) ?: return null)
                else -> {
                    val lastMatchArm = body.matchArmList.lastOrNull()
                    val placeForComma = if (lastMatchArm != null && lastMatchArm.expr !is RsBlockExpr && lastMatchArm.comma == null) {
                        PsiInsertionPlace.afterLastChildIn(lastMatchArm) ?: return null
                    } else {
                        null
                    }
                    val rbrace = body.rbrace
                    val placeForArms = if (rbrace != null) {
                        PsiInsertionPlace.before(rbrace)
                    } else {
                        PsiInsertionPlace.afterLastChildIn(body)
                    } ?: return null
                    ArmsInsertionPlace.ToExistingBody(placeForComma, placeForArms)
                }
            }
        }
    }
}

class AddWildcardArmFix(match: RsMatchExpr) : AddRemainingArmsFix(match, emptyList()) {
    override fun getFamilyName(): String = NAME
    override fun getText(): String = familyName

    override fun createNewArms(psiFactory: RsPsiFactory, context: RsElement): List<RsMatchArm> = listOf(
        psiFactory.createMatchBody(listOf(Pattern.wild())).matchArmList.first()
    )

    companion object {
        @IntentionName
        @IntentionFamilyName
        val NAME = RsBundle.message("intention.name.add.pattern")
    }
}
