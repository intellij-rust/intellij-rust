/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import org.rust.lang.core.macros.tt.TokenTree
import org.rust.lang.core.macros.tt.TokenTreeJsonSerializer
import org.rust.stdext.exhaustive
import org.rust.util.RsJacksonSerializer

// This is a sealed class because there is `ListMacro` request kind which we don't use for now
sealed class Request {
    // data class ListMacro(...)
    data class ExpansionMacro(
        val macroBody: TokenTree.Subtree,
        val macroName: String,
        val attributes: TokenTree.Subtree?,
        val lib: String,
        val env: List<List<String>>
    ) : Request()
}

class RequestJsonSerializer : RsJacksonSerializer<Request>(Request::class.java) {
    override fun serialize(request: Request, gen: JsonGenerator, provider: SerializerProvider) {
        when (request) {
            is Request.ExpansionMacro -> gen.writeJsonObjectWithSingleField("ExpansionMacro") {
                writeJsonObject {
                    writeField("macro_body") { TokenTreeJsonSerializer.writeSubtree(request.macroBody, gen) }
                    writeStringField("macro_name", request.macroName)
                    writeNullableField("attributes", request.attributes) { attributes ->
                        TokenTreeJsonSerializer.writeSubtree(attributes, gen)
                    }
                    writeStringField("lib", request.lib)
                    writeArrayField("env", request.env) { list ->
                        writeArray(list) { writeString(it) }
                    }
                }
            }
        }.exhaustive
    }

}
