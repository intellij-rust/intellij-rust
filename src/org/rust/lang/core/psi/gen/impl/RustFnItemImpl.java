// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi.gen.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.rust.lang.core.psi.gen.RustElementTypes.*;
import org.rust.lang.core.psi.impl.RustCompositeElementImpl;
import org.rust.lang.core.psi.gen.*;

public class RustFnItemImpl extends RustCompositeElementImpl implements RustFnItem {

  public RustFnItemImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitFnItem(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<RustDeclItem> getDeclItemList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustDeclItem.class);
  }

  @Override
  @NotNull
  public List<RustDeclStmt> getDeclStmtList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustDeclStmt.class);
  }

  @Override
  @Nullable
  public RustExpr getExpr() {
    return findChildByClass(RustExpr.class);
  }

  @Override
  @NotNull
  public List<RustExprStmt> getExprStmtList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustExprStmt.class);
  }

}
