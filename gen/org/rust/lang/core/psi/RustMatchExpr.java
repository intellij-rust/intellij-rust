// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustMatchExpr extends RustExpr {

  @NotNull
  List<RustBlock> getBlockList();

  @NotNull
  List<RustExpr> getExprList();

  @NotNull
  List<RustOuterAttr> getOuterAttrList();

  @NotNull
  List<RustPat> getPatList();

  @NotNull
  PsiElement getLbrace();

  @NotNull
  PsiElement getMatch();

  @NotNull
  PsiElement getRbrace();

}
