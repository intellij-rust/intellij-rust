// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustTupleExpr extends RustExpr {

  @NotNull
  List<RustExpr> getExprList();

  @NotNull
  PsiElement getLparen();

  @NotNull
  PsiElement getRparen();

}
