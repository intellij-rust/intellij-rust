// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustWhileExpr extends RustExpr {

  @NotNull
  RustBlock getBlock();

  @NotNull
  RustExpr getExpr();

  @NotNull
  PsiElement getColon();

  @NotNull
  PsiElement getLbrace();

  @Nullable
  PsiElement getLifetime();

  @NotNull
  PsiElement getRbrace();

  @Nullable
  PsiElement getStaticLifetime();

  @NotNull
  PsiElement getWhile();

}
