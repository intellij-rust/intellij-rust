// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustLoopExpr extends RustExpr {

  @NotNull
  RustBlock getBlock();

  @NotNull
  PsiElement getColon();

  @NotNull
  PsiElement getLbrace();

  @Nullable
  PsiElement getLifetime();

  @NotNull
  PsiElement getLoop();

  @NotNull
  PsiElement getRbrace();

  @Nullable
  PsiElement getStaticLifetime();

}
