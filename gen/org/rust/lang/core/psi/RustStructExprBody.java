// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustStructExprBody extends RustCompositeElement {

  @NotNull
  RustCommaSeparatedList getCommaSeparatedList();

  @Nullable
  RustExpr getExpr();

  @Nullable
  PsiElement getDotdot();

  @NotNull
  PsiElement getLbrace();

  @NotNull
  PsiElement getRbrace();

}
