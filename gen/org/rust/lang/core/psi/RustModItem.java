// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustModItem extends RustCompositeElement {

  @NotNull
  List<RustExternCrateDecl> getExternCrateDeclList();

  @NotNull
  List<RustItem> getItemList();

  @NotNull
  List<RustUseDecl> getUseDeclList();

  @Nullable
  PsiElement getLbrace();

  @NotNull
  PsiElement getMod();

  @Nullable
  PsiElement getRbrace();

  @NotNull
  PsiElement getIdentifier();

}
