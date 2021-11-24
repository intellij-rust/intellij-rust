/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import gnu.trove.TIntArrayList
import org.rust.util.RsJacksonDeserializer

object FlatTreeJsonDeserializer : RsJacksonDeserializer<FlatTree>(FlatTree::class.java) {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): FlatTree {
        val subtree = TIntArrayList()
        val literal = TIntArrayList()
        val punct = TIntArrayList()
        val ident = TIntArrayList()
        val tokenTree = TIntArrayList()
        val text = mutableListOf<String>()

        context.readObjectFields { key1 ->
            when (key1) {
                "subtree" -> readIntArray(subtree)
                "literal" -> readIntArray(literal)
                "punct" -> readIntArray(punct)
                "ident" -> readIntArray(ident)
                "token_tree" -> readIntArray(tokenTree)
                "text" -> readArray(text) { readString() }
            }
        }

        return FlatTree(subtree, literal, punct, ident, tokenTree, text)
    }

    private fun DeserializationContext.readIntArray(out: TIntArrayList) {
        expectToken(JsonToken.START_ARRAY)
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            out.add(readLong().toInt())
        }
    }
}
