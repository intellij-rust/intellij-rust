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

public class RustWhereClauseImpl extends RustCompositeElementImpl implements RustWhereClause {

  public RustWhereClauseImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitWhereClause(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<RustWherePred> getWherePredList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustWherePred.class);
  }

  @Override
  @NotNull
  public PsiElement getWhere() {
    return findNotNullChildByType(WHERE);
  }

}
