// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustFnItem extends RustCompositeElement {

  @Nullable
  RustBlock getBlock();

  @NotNull
  List<RustDeclItem> getDeclItemList();

  @Nullable
  PsiElement getArrow();

  @NotNull
  PsiElement getFn();

  @Nullable
  PsiElement getLparen();

  @Nullable
  PsiElement getRparen();

}
