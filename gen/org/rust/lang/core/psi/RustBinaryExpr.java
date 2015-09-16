// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustBinaryExpr extends RustExpr {

  @NotNull
  List<RustExpr> getExprList();

  @Nullable
  PsiElement getAnd();

  @Nullable
  PsiElement getAndand();

  @Nullable
  PsiElement getAndeq();

  @Nullable
  PsiElement getDiv();

  @Nullable
  PsiElement getDiveq();

  @Nullable
  PsiElement getEq();

  @Nullable
  PsiElement getEqeq();

  @Nullable
  PsiElement getExcleq();

  @Nullable
  PsiElement getGt();

  @Nullable
  PsiElement getGteq();

  @Nullable
  PsiElement getGtgt();

  @Nullable
  PsiElement getGtgteq();

  @Nullable
  PsiElement getLt();

  @Nullable
  PsiElement getLteq();

  @Nullable
  PsiElement getLtlt();

  @Nullable
  PsiElement getLtlteq();

  @Nullable
  PsiElement getMinus();

  @Nullable
  PsiElement getMinuseq();

  @Nullable
  PsiElement getMul();

  @Nullable
  PsiElement getMuleq();

  @Nullable
  PsiElement getOr();

  @Nullable
  PsiElement getOreq();

  @Nullable
  PsiElement getOror();

  @Nullable
  PsiElement getPlus();

  @Nullable
  PsiElement getPluseq();

  @Nullable
  PsiElement getRem();

  @Nullable
  PsiElement getRemeq();

  @Nullable
  PsiElement getXor();

  @Nullable
  PsiElement getXoreq();

  @NotNull
  RustExpr getLeft();

  @Nullable
  RustExpr getRight();

}
