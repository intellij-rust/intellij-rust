// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustForExpr extends RustExpr {

  @NotNull
  RustBlock getBlock();

  @NotNull
  RustExpr getExpr();

  @NotNull
  RustPat getPat();

  @Nullable
  PsiElement getColon();

  @NotNull
  PsiElement getFor();

  @NotNull
  PsiElement getIn();

  @Nullable
  PsiElement getLifetime();

  @Nullable
  PsiElement getStaticLifetime();

}
