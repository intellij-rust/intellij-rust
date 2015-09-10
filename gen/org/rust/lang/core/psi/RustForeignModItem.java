// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustForeignModItem extends RustCompositeElement {

  @Nullable
  RustAbi getAbi();

  @NotNull
  List<RustForeignItem> getForeignItemList();

  @NotNull
  List<RustInnerAttr> getInnerAttrList();

  @NotNull
  PsiElement getExtern();

  @NotNull
  PsiElement getLbrace();

  @NotNull
  PsiElement getRbrace();

}
