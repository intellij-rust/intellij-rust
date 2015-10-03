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

public class RustPathWithoutColonsImpl extends RustCompositeElementImpl implements RustPathWithoutColons {

  public RustPathWithoutColonsImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitPathWithoutColons(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<RustBindings> getBindingsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustBindings.class);
  }

  @Override
  @NotNull
  public List<RustLifetimes> getLifetimesList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustLifetimes.class);
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
