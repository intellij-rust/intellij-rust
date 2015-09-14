// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.rust.lang.core.psi.RustCompositeElementTypes.*;
import org.rust.lang.core.psi.*;

public class RustTraitRefImpl extends RustCompositeElementImpl implements RustTraitRef {

  public RustTraitRefImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitTraitRef(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<RustGenericArgs> getGenericArgsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustGenericArgs.class);
  }

  @Override
  @NotNull
  public List<RustRetType> getRetTypeList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustRetType.class);
  }

  @Override
  @NotNull
  public List<RustTypeSums> getTypeSumsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustTypeSums.class);
  }

}
