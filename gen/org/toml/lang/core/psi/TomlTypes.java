// This is a generated file. Not intended for manual editing.
package org.toml.lang.core.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import org.toml.lang.core.lexer.TomlTokenType;
import org.toml.lang.core.psi.impl.*;

public interface TomlTypes {

  IElementType ARRAY = new TomlElementType("ARRAY");
  IElementType EXPRESSION = new TomlElementType("EXPRESSION");
  IElementType KEY_VALUE = new TomlElementType("KEY_VALUE");
  IElementType PATH = new TomlElementType("PATH");
  IElementType TABLE = new TomlElementType("TABLE");
  IElementType TABLE_ENTRIES = new TomlElementType("TABLE_ENTRIES");
  IElementType TABLE_HEADER = new TomlElementType("TABLE_HEADER");
  IElementType VALUE = new TomlElementType("VALUE");

  IElementType BOOLEAN = new TomlTokenType("boolean");
  IElementType COMMENT = new TomlTokenType("comment");
  IElementType KEY = new TomlTokenType("key");
  IElementType NUMBER = new TomlTokenType("number");
  IElementType STRING = new TomlTokenType("string");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
       if (type == ARRAY) {
        return new TomlArrayImpl(node);
      }
      else if (type == EXPRESSION) {
        return new TomlExpressionImpl(node);
      }
      else if (type == KEY_VALUE) {
        return new TomlKeyValueImpl(node);
      }
      else if (type == PATH) {
        return new TomlPathImpl(node);
      }
      else if (type == TABLE) {
        return new TomlTableImpl(node);
      }
      else if (type == TABLE_ENTRIES) {
        return new TomlTableEntriesImpl(node);
      }
      else if (type == TABLE_HEADER) {
        return new TomlTableHeaderImpl(node);
      }
      else if (type == VALUE) {
        return new TomlValueImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
