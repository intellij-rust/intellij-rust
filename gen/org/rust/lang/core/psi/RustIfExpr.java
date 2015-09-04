// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustIfExpr extends RustExpr {

  @NotNull
  List<RustBlock> getBlockList();

  @Nullable
  RustExpr getExpr();

  @Nullable
  PsiElement getElse();

  @NotNull
  PsiElement getIf();

}
