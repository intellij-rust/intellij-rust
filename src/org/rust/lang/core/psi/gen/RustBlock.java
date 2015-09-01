// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi.gen;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import org.rust.lang.core.psi.RustCompositeElement;

public interface RustBlock extends RustCompositeElement {

  @NotNull
  List<RustDeclStmt> getDeclStmtList();

  @Nullable
  RustExpr getExpr();

  @NotNull
  List<RustExprStmt> getExprStmtList();

}
