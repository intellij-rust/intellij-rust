// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustIfLetExpr extends RustExpr {

  @NotNull
  List<RustBlock> getBlockList();

  @NotNull
  List<RustExpr> getExprList();

  @NotNull
  RustPat getPat();

  @Nullable
  PsiElement getElse();

  @NotNull
  PsiElement getEq();

  @NotNull
  PsiElement getIf();

  @NotNull
  PsiElement getLet();

}
