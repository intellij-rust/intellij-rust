/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

class TomlSchemaCompletionTest : TomlByJsonSchemaCompletionTestBase() {
    fun `test bare keys completion`() = testBySchema("""
        {
          "properties": {
            "a": {}, "b": {}, "c": {}
          }
        }
    """,
    """
        <caret>
    """, "a", "b", "c")

    fun `test completion inside table`() = testBySchema("""
        {
          "properties": {
            "foo": {
              "properties": {
                "bar": {}, "buz": {}
              }
            }
          }
        }
    """,
    """
        [foo]
        b<caret>
    """, "bar", "buz")

    fun `test completion in key segments`() = testBySchema("""
        {
          "properties": {
            "foo": {
              "properties": {
                "bar": {}, "buz": {}
              }
            }
          }
        }
    """,
        """
        foo.<caret>
    """, "bar", "buz")

    fun `test completion in table key`() = testBySchema("""
        {
          "properties": {
            "foo": {
              "properties": {
                "bar": {}, "buz": {}
              }
            }
          }
        }
    """,
        """
        [foo.<caret>]
    """, "bar", "buz")

    fun `test completion in array key`() = testBySchema("""
        {
          "properties": {
            "foo": {
              "properties": {
                "bar": {}, "buz": {}
              }
            }
          }
        }
    """,
        """
        [[foo.<caret>]]
    """, "bar", "buz")
}
