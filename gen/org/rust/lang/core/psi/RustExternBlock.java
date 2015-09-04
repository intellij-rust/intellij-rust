// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustExternBlock extends RustCompositeElement {

  @NotNull
  List<RustForeignFnItem> getForeignFnItemList();

  @NotNull
  PsiElement getExtern();

  @NotNull
  PsiElement getLbrace();

  @NotNull
  PsiElement getRbrace();

}
