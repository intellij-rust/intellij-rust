// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustItemMacro extends RustCompositeElement {

  @NotNull
  RustPathExpr getPathExpr();

  @Nullable
  RustTokenTree getTokenTree();

  @NotNull
  PsiElement getExcl();

  @Nullable
  PsiElement getIdentifier();

  @Nullable
  PsiElement getLbrace();

  @Nullable
  PsiElement getLbrack();

  @Nullable
  PsiElement getLparen();

  @Nullable
  PsiElement getRbrace();

  @Nullable
  PsiElement getRbrack();

  @Nullable
  PsiElement getRparen();

  @Nullable
  PsiElement getSemicolon();

}
