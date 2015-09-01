// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi.gen;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustBlockExpr extends RustExpr {

  @NotNull
  List<RustDeclStmt> getDeclStmtList();

  @NotNull
  RustExpr getExpr();

  @NotNull
  List<RustExprStmt> getExprStmtList();

  @NotNull
  List<RustItem> getItemList();

}
