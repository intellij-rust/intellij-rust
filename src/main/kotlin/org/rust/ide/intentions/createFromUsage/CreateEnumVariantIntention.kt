/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.createFromUsage

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.ide.intentions.createFromUsage.CreateEnumVariantIntention.Context
import org.rust.ide.utils.PsiInsertionPlace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class CreateEnumVariantIntention : RsElementBaseIntentionAction<Context>() {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.create.enum.variant")

    class Context(
        val path: RsPath,
        val enum: RsEnumItem,
        val name: String,
        val place: PsiInsertionPlace,
        val commaPlace: PsiInsertionPlace?,
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = (element.context as? RsPath)?.takeIf { it.identifier == element } ?: return null
        if (path.context is RsPath) return null
        if (path.resolveStatus != PathResolveStatus.UNRESOLVED) return null
        val name = path.referenceName ?: return null
        if (name.first().isLowerCase()) return null

        val qualifier = path.qualifier ?: return null
        val enum = qualifier.reference?.resolve() as? RsEnumItem ?: return null
        if (enum.variants.any { it.name == name }) return null

        val lastVariant = enum.variants.lastOrNull()?.takeIf { !it.hasTrailingComma }
        val commaPlace = lastVariant?.let { PsiInsertionPlace.after(it) ?: return null }
        val rbrace = enum.enumBody?.rbrace ?: return null
        val place = PsiInsertionPlace.before(rbrace) ?: return null

        text = RsBundle.message("intention.name.create.enum.variant", name)
        return Context(path, enum, name, place, commaPlace)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)

        val fields = generateFields(ctx.path)
        val variant = factory.createEnumVariant("${ctx.name}$fields")

        ctx.commaPlace?.insert(factory.createComma())
        ctx.place.insertMultiple(variant, factory.createComma(), factory.createNewline())
    }

    private fun generateFields(path: RsPath): String {
        val context = path.context
        if (context is RsStructLiteral) {
            return CreateStructIntention.generateFields(context, "").replace('\n', ' ')
        }
        val context2 = context?.context
        if (context is RsPathExpr && context2 is RsCallExpr) {
            return CreateTupleStructIntention.generateFields(context2, "")
        }
        return ""
    }

    private val RsEnumVariant.hasTrailingComma: Boolean
        get() = getNextNonCommentSibling()?.elementType == RsElementTypes.COMMA

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY
}
