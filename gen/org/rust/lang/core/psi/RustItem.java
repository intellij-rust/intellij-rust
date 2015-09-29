// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustItem extends RustCompositeElement {

  @Nullable
  RustItemMacro getItemMacro();

  @Nullable
  RustModItem getModItem();

  @NotNull
  List<RustOuterAttr> getOuterAttrList();

  @Nullable
  RustStmtItem getStmtItem();

  @Nullable
  PsiElement getPub();

}
