/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.refaster;

import com.google.auto.value.AutoValue;
import com.google.errorprone.util.ASTHelpers;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;

import javax.annotation.Nullable;
import javax.lang.model.element.Name;

/**
 * {@link UTree} version of {@link MemberSelectTree}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class UMemberSelect extends UExpression implements MemberSelectTree {

  /**
   * Use of this string as an expression in a member select will cause this method select
   * to be inlined as an identifier. I.e., "".foo will be inlined as foo.
   */
  public static final String CONVERT_TO_IDENT = "";
  
  public static UMemberSelect create(UExpression expression, String identifier, UType type) {
    return new AutoValue_UMemberSelect(expression, identifier, type);
  }
  
  @Override
  public abstract UExpression getExpression();
  
  abstract String identifier();
  
  abstract UType type();

  @Override
  @Nullable
  public Unifier visitMemberSelect(MemberSelectTree fieldAccess, @Nullable Unifier unifier) {
    if (ASTHelpers.getSymbol(fieldAccess) != null) {
      unifier = fieldAccess.getIdentifier().contentEquals(identifier()) ? unifier : null;
      unifier = getExpression().unify(fieldAccess.getExpression(), unifier);
      return type().unify(ASTHelpers.getSymbol(fieldAccess).asType(), unifier);
    }
    return null;
  }

  @Override
  @Nullable
  public Unifier visitIdentifier(IdentifierTree ident, @Nullable Unifier unifier) {
    if (ident.getName().contentEquals(identifier())) {
      // We artificially create a "this" expression and then unify the template receiver with that.
      JCExpression thisIdent = unifier.thisExpression(receiverType(ident));
      unifier = getExpression().unify(thisIdent, unifier);
      return type().unify(ASTHelpers.getSymbol(ident).asType(), unifier);
    }
    return null;
  }
  
  private static Type receiverType(ExpressionTree expressionTree) {
    if (expressionTree instanceof JCFieldAccess) {
      JCFieldAccess methodSelectFieldAccess = (JCFieldAccess) expressionTree;
      return methodSelectFieldAccess.sym.owner.type;
    } else if (expressionTree instanceof JCIdent) {
      JCIdent methodCall = (JCIdent) expressionTree;
      return methodCall.sym.owner.type;
    }
    throw new IllegalArgumentException("Expected a JCFieldAccess or JCIdent");
  }

  @Override
  public Kind getKind() {
    return Kind.MEMBER_SELECT;
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitMemberSelect(this, data);
  }

  @Override
  public Name getIdentifier() {
    return StringName.of(identifier());
  }

  @Override
  public JCExpression inline(Inliner inliner) throws CouldNotResolveImportException {
    JCExpression expression = getExpression().inline(inliner);
    if (expression.toString().equals(CONVERT_TO_IDENT)) {
      return inliner.maker().Ident(inliner.asName(identifier()));
    }
    return inliner.maker().Select(getExpression().inline(inliner), inliner.asName(identifier()));
  }
}
