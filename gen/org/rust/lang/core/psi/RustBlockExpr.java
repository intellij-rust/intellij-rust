// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustBlockExpr extends RustExpr {

  @Nullable
  RustExpr getExpr();

  @NotNull
  List<RustItem> getItemList();

  @NotNull
  List<RustStmt> getStmtList();

  @NotNull
  PsiElement getLbrace();

  @Nullable
  PsiElement getRbrace();

}
