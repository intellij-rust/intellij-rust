/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import org.rust.util.RsJacksonSerializer

object FlatTreeJsonSerializer : RsJacksonSerializer<FlatTree>(FlatTree::class.java) {
    override fun serialize(tt: FlatTree, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeJsonObject {
            writeArrayField("subtree", tt.subtree) { writeNumber(it.toLong() and 0xFFFFFFFFL) }
            writeArrayField("literal", tt.literal) { writeNumber(it.toLong() and 0xFFFFFFFFL) }
            writeArrayField("punct", tt.punct) { writeNumber(it.toLong() and 0xFFFFFFFFL) }
            writeArrayField("ident", tt.ident) { writeNumber(it.toLong() and 0xFFFFFFFFL) }
            writeArrayField("token_tree", tt.tokenTree) { writeNumber(it.toLong() and 0xFFFFFFFFL) }
            writeArrayField("text", tt.text) { writeString(it) }
        }
    }
}
