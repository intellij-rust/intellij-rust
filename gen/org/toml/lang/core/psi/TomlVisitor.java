// This is a generated file. Not intended for manual editing.
package org.toml.lang.core.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class TomlVisitor extends PsiElementVisitor {

  public void visitArray(@NotNull TomlArray o) {
    visitPsiElement(o);
  }

  public void visitExpression(@NotNull TomlExpression o) {
    visitPsiElement(o);
  }

  public void visitKeyValue(@NotNull TomlKeyValue o) {
    visitPsiElement(o);
  }

  public void visitPath(@NotNull TomlPath o) {
    visitPsiElement(o);
  }

  public void visitTable(@NotNull TomlTable o) {
    visitPsiElement(o);
  }

  public void visitTableEntries(@NotNull TomlTableEntries o) {
    visitPsiElement(o);
  }

  public void visitTableHeader(@NotNull TomlTableHeader o) {
    visitPsiElement(o);
  }

  public void visitValue(@NotNull TomlValue o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
