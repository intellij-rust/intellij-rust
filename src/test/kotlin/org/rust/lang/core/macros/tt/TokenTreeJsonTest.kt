/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.intellij.util.io.jackson.IntelliJPrettyPrinter
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.macros.proc.ProcMacroJsonParser
import org.rust.lang.core.parser.createRustPsiBuilder

class TokenTreeJsonTest : RsTestBase() {
    fun `test 1`() = doTest(".", """
        {
          "delimiter": null,
          "token_trees": [
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Alone",
                  "id": 0
                }
              }
            }
          ]
        }
    """)

    fun `test 2`() = doTest("..", """
        {
          "delimiter": null,
          "token_trees": [
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Joint",
                  "id": 0
                }
              }
            },
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Alone",
                  "id": 1
                }
              }
            }
          ]
        }
    """)

    fun `test 3`() = doTest(".foo", """
        {
          "delimiter": null,
          "token_trees": [
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Alone",
                  "id": 0
                }
              }
            },
            {
              "Leaf": {
                "Ident": {
                  "text": "foo",
                  "id": 1
                }
              }
            }
          ]
        }
    """)

    fun `test 4`() = doTest(":::", """
        {
          "delimiter": null,
          "token_trees": [
            {
              "Leaf": {
                "Punct": {
                  "char": ":",
                  "spacing": "Joint",
                  "id": 0
                }
              }
            },
            {
              "Leaf": {
                "Punct": {
                  "char": ":",
                  "spacing": "Joint",
                  "id": 1
                }
              }
            },
            {
              "Leaf": {
                "Punct": {
                  "char": ":",
                  "spacing": "Alone",
                  "id": 2
                }
              }
            }
          ]
        }
    """)

    fun `test 5`() = doTest(". asd .. \"asd\" ...", """
        {
          "delimiter": null,
          "token_trees": [
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Alone",
                  "id": 0
                }
              }
            },
            {
              "Leaf": {
                "Ident": {
                  "text": "asd",
                  "id": 1
                }
              }
            },
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Joint",
                  "id": 2
                }
              }
            },
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Alone",
                  "id": 3
                }
              }
            },
            {
              "Leaf": {
                "Literal": {
                  "text": "\"asd\"",
                  "id": 4
                }
              }
            },
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Joint",
                  "id": 5
                }
              }
            },
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Joint",
                  "id": 6
                }
              }
            },
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Alone",
                  "id": 7
                }
              }
            }
          ]
        }
    """)

    fun `test 6`() = doTest("{}", """
        {
          "delimiter": {
            "id": 0,
            "kind": "Brace"
          },
          "token_trees": [ ]
        }
    """)

    fun `test 7`() = doTest("[]", """
        {
          "delimiter": {
            "id": 0,
            "kind": "Bracket"
          },
          "token_trees": [ ]
        }
    """)

    fun `test 8`() = doTest("()", """
        {
          "delimiter": {
            "id": 0,
            "kind": "Parenthesis"
          },
          "token_trees": [ ]
        }
    """)

    fun `test 9`() = doTest("(..)", """
        {
          "delimiter": {
            "id": 0,
            "kind": "Parenthesis"
          },
          "token_trees": [
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Joint",
                  "id": 1
                }
              }
            },
            {
              "Leaf": {
                "Punct": {
                  "char": ".",
                  "spacing": "Joint",
                  "id": 2
                }
              }
            }
          ]
        }
    """)

    fun `test 10`() = doTest("([{.}])", """
        {
          "delimiter": {
            "id": 0,
            "kind": "Parenthesis"
          },
          "token_trees": [
            {
              "Subtree": {
                "delimiter": {
                  "id": 1,
                  "kind": "Bracket"
                },
                "token_trees": [
                  {
                    "Subtree": {
                      "delimiter": {
                        "id": 2,
                        "kind": "Brace"
                      },
                      "token_trees": [
                        {
                          "Leaf": {
                            "Punct": {
                              "char": ".",
                              "spacing": "Joint",
                              "id": 3
                            }
                          }
                        }
                      ]
                    }
                  }
                ]
              }
            }
          ]
        }
    """)

    fun doTest(code: String, @Language("Json") expectedJson: String) {
        val expectedJson2 = expectedJson
            .trimIndent()
            .lineSequence()
            .drop(1)
            .joinToString(prefix = "{\n  \"Subtree\": {\n", separator = "\n", postfix = "\n}") { "  $it" }

        val subtree = project.createRustPsiBuilder(code).parseSubtree().subtree

        val jackson = ProcMacroJsonParser.jackson
        val actualJson = jackson
            .writer()
            .with(DefaultPrettyPrinter(IntelliJPrettyPrinter()))
            .writeValueAsString(subtree)

        assertEquals(expectedJson2, actualJson)
        assertEquals(jackson.readValue(actualJson, TokenTree::class.java), subtree)
    }
}
