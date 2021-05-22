/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.intellij.openapi.project.Project
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.tt.MappedSubtree
import org.rust.lang.core.macros.tt.TokenTree
import org.rust.lang.core.macros.tt.parseSubtree
import org.rust.lang.core.macros.tt.toMappedText
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import java.io.IOException
import java.util.concurrent.TimeoutException

class ProcMacroExpander(
    private val project: Project,
    private val server: ProcMacroServerPool? = ProcMacroApplicationService.getInstance().getServer()
) : MacroExpander<RsProcMacroData, ProcMacroExpansionError>() {

    override fun expandMacroAsTextWithErr(
        def: RsProcMacroData,
        call: RsMacroCallData
    ): RsResult<Pair<CharSequence, RangeMap>, ProcMacroExpansionError> {
        val (macroCallBodyText, attrText) = when (val macroBody = call.macroBody) {
            is MacroCallBody.Attribute -> macroBody.item to macroBody.attr
            is MacroCallBody.Derive -> MappedText.single(macroBody.item, 0) to null
            is MacroCallBody.FunctionLike -> MappedText.single(macroBody.text, 0) to null
            null -> return Err(ProcMacroExpansionError.MacroCallSyntax)
        }
        val env = call.packageEnv
        val (macroCallBodyLowered, rangesLowering) = project
            .createRustPsiBuilder(macroCallBodyText.text)
            .lowerDocCommentsToPsiBuilder(project)
        val (macroCallBodyTt, tokenMap) = macroCallBodyLowered.parseSubtree()
        // TODO support mapping for the attribute value
        val attrSubtree = attrText?.let { project.createRustPsiBuilder(it.text).parseSubtree() }?.subtree?.copy(delimiter = null)
        return expandMacroAsTtWithErr(macroCallBodyTt, attrSubtree, def.name, def.artifact.path.toString(), env).map {
            val (text, ranges) = MappedSubtree(it, tokenMap).toMappedText()
            text to macroCallBodyText.ranges.mapAll(rangesLowering.mapAll(ranges))
        }
    }

    fun expandMacroAsTtWithErr(
        macroCallBody: TokenTree.Subtree,
        attributes: TokenTree.Subtree?,
        macroName: String,
        lib: String,
        env: Map<String, String> = emptyMap()
    ): RsResult<TokenTree.Subtree, ProcMacroExpansionError> {
        val server = server ?: return Err(ProcMacroExpansionError.ExecutableNotFound)
        val envList = env.map { listOf(it.key, it.value) }
        val response = try {
            server.send(Request.ExpansionMacro(macroCallBody, macroName, attributes, lib, envList))
        } catch (ignored: TimeoutException) {
            return Err(ProcMacroExpansionError.Timeout)
        } catch (e: ProcessCreationException) {
            MACRO_LOG.warn("Failed to run proc macro expander", e)
            return Err(ProcMacroExpansionError.CantRunExpander)
        } catch (e: IOException) {
            return Err(ProcMacroExpansionError.ExceptionThrown(e))
        }
        return when (response) {
            is Response.ExpansionMacro -> Ok(response.expansion)
            is Response.Error -> Err(ProcMacroExpansionError.ServerSideError(response.message))
        }
    }

    companion object {
        const val EXPANDER_VERSION: Int = 1
    }
}
