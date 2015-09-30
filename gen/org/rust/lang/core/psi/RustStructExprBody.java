// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustStructExprBody extends RustCompositeElement {

  @NotNull
  List<RustExpr> getExprList();

  @Nullable
  PsiElement getDotdot();

  @NotNull
  PsiElement getLbrace();

  @NotNull
  PsiElement getRbrace();

}
