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

public class RustTypeSumsImpl extends RustCompositeElementImpl implements RustTypeSums {

  public RustTypeSumsImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitTypeSums(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<RustTypeSum> getTypeSumList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustTypeSum.class);
  }

}
