// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustLambdaExpr extends RustExpr {

  @Nullable
  RustExpr getExpr();

  @NotNull
  List<RustPat> getPatList();

  @Nullable
  RustRetType getRetType();

  @NotNull
  List<RustTypeAscription> getTypeAscriptionList();

  @Nullable
  PsiElement getMove();

  @Nullable
  PsiElement getOror();

}
