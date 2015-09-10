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

public class RustStructItemImpl extends RustNamedElementImpl implements RustStructItem {

  public RustStructItemImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitStructItem(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public RustGenericParams getGenericParams() {
    return findNotNullChildByClass(RustGenericParams.class);
  }

  @Override
  @Nullable
  public RustStructDeclArgs getStructDeclArgs() {
    return findChildByClass(RustStructDeclArgs.class);
  }

  @Override
  @Nullable
  public RustStructTupleArgs getStructTupleArgs() {
    return findChildByClass(RustStructTupleArgs.class);
  }

  @Override
  @Nullable
  public RustWhereClause getWhereClause() {
    return findChildByClass(RustWhereClause.class);
  }

  @Override
  @NotNull
  public PsiElement getIdentifier() {
    return findNotNullChildByType(IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getSemicolon() {
    return findChildByType(SEMICOLON);
  }

  @Override
  @NotNull
  public PsiElement getStruct() {
    return findNotNullChildByType(STRUCT);
  }

}
