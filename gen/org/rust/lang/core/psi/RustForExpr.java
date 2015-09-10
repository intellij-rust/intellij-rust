// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustForExpr extends RustExpr {

  @NotNull
  RustBlock getBlock();

  @NotNull
  RustPat getPat();

  @NotNull
  PsiElement getColon();

  @NotNull
  PsiElement getFor();

  @NotNull
  PsiElement getIn();

  @NotNull
  PsiElement getLbrace();

  @Nullable
  PsiElement getLifetime();

  @NotNull
  PsiElement getRbrace();

  @Nullable
  PsiElement getStaticLifetime();

}
