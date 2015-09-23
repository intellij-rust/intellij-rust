// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustWhileLetExpr extends RustExpr {

  @Nullable
  RustBlock getBlock();

  @Nullable
  RustExpr getExpr();

  @NotNull
  RustPat getPat();

  @NotNull
  PsiElement getEq();

  @NotNull
  PsiElement getLet();

  @NotNull
  PsiElement getWhile();

}
