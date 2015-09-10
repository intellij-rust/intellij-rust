// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustModItem extends RustCompositeElement {

  @NotNull
  List<RustInnerAttr> getInnerAttrList();

  @NotNull
  List<RustItem> getItemList();

  @NotNull
  List<RustOuterAttr> getOuterAttrList();

  @Nullable
  PsiElement getLbrace();

  @NotNull
  PsiElement getMod();

  @Nullable
  PsiElement getRbrace();

  @Nullable
  PsiElement getSemicolon();

  @NotNull
  PsiElement getIdentifier();

}
