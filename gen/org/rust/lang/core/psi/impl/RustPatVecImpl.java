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

public class RustPatVecImpl extends RustCompositeElementImpl implements RustPatVec {

  public RustPatVecImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitPatVec(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<RustPat> getPatList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustPat.class);
  }

  @Override
  @Nullable
  public PsiElement getDotdot() {
    return findChildByType(DOTDOT);
  }

}
