// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustPathGlob extends RustCompositeElement {

  @NotNull
  List<RustPath> getPathList();

  @Nullable
  RustPathGlob getPathGlob();

  @Nullable
  PsiElement getColoncolon();

  @Nullable
  PsiElement getLbrace();

  @Nullable
  PsiElement getMul();

  @Nullable
  PsiElement getRbrace();

  @Nullable
  PsiElement getIdentifier();

}
