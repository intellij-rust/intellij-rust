// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustTraitType extends RustCompositeElement {

  @NotNull
  List<RustOuterAttr> getOuterAttrList();

  @NotNull
  RustTypeParam getTypeParam();

  @NotNull
  PsiElement getSemicolon();

  @NotNull
  PsiElement getType();

}
