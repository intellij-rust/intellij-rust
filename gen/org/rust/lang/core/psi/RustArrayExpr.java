// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustArrayExpr extends RustExpr {

  @NotNull
  List<RustExpr> getExprList();

  @NotNull
  PsiElement getLbrack();

  @Nullable
  PsiElement getRbrack();

  @Nullable
  PsiElement getSemicolon();

}
