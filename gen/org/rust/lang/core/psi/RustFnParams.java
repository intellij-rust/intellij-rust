// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustFnParams extends RustCompositeElement {

  @NotNull
  List<RustParam> getParamList();

  @NotNull
  PsiElement getLparen();

  @Nullable
  PsiElement getRparen();

}
