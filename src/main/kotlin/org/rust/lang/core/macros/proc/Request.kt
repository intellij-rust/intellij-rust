/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import org.rust.lang.core.macros.tt.FlatTree
import org.rust.lang.core.macros.tt.FlatTreeJsonSerializer
import org.rust.stdext.exhaustive
import org.rust.util.RsJacksonSerializer

// This is a sealed class because there is `ListMacro` request kind which we don't use for now
sealed class Request {
    // data class ListMacro(...)
    data class ExpandMacro(
        val macroBody: FlatTree,
        val macroName: String,
        val attributes: FlatTree?,
        val lib: String,
        val env: List<List<String>>
    ) : Request()
}

class RequestJsonSerializer : RsJacksonSerializer<Request>(Request::class.java) {
    override fun serialize(request: Request, gen: JsonGenerator, provider: SerializerProvider) {
        when (request) {
            is Request.ExpandMacro -> gen.writeJsonObjectWithSingleField("ExpandMacro") {
                writeJsonObject {
                    writeField("macro_body") { FlatTreeJsonSerializer.serialize(request.macroBody, gen, provider) }
                    writeStringField("macro_name", request.macroName)
                    writeNullableField("attributes", request.attributes) { attributes ->
                        FlatTreeJsonSerializer.serialize(attributes, gen, provider)
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
