/*
 * Copyright 2013 Google Inc. All rights reserved.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableBiMap;

import com.sun.source.tree.TreeVisitor;
import com.sun.source.tree.UnaryTree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.TreeInfo;

import javax.annotation.Nullable;

/**
 * {@link UTree} version of {@link UnaryTree}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class UUnary extends UExpression implements UnaryTree {
  private static final ImmutableBiMap<Kind, Integer> UNARY_OP_CODES =
      new ImmutableBiMap.Builder<Kind, Integer>()
        .put(Kind.PREFIX_INCREMENT, JCTree.PREINC)
        .put(Kind.PREFIX_DECREMENT, JCTree.PREDEC)
        .put(Kind.POSTFIX_INCREMENT, JCTree.POSTINC)
        .put(Kind.POSTFIX_DECREMENT, JCTree.POSTDEC)
        .put(Kind.UNARY_PLUS, JCTree.POS)
        .put(Kind.UNARY_MINUS, JCTree.NEG)
        .put(Kind.BITWISE_COMPLEMENT, JCTree.COMPL)
        .put(Kind.LOGICAL_COMPLEMENT, JCTree.NOT)
        .build();

  public static UUnary create(Kind unaryOp, UExpression expression) {
    checkArgument(UNARY_OP_CODES.containsKey(unaryOp), 
        "%s is not a recognized unary operation", unaryOp);
    return new AutoValue_UUnary(unaryOp, expression);
  }

  @Override
  public abstract Kind getKind();

  @Override
  public abstract UExpression getExpression();

  @Override
  @Nullable
  public Unifier unify(JCTree target, @Nullable Unifier unifier) {
    if (unifier != null && target instanceof JCUnary) {
      JCUnary unary = (JCUnary) target;
      unifier = getKind().equals(unary.getKind()) ? unifier : null;
      return getExpression().unify(TreeInfo.skipParens(unary.getExpression()), unifier);
    }
    return null;
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitUnary(this, data);
  }

  @Override
  public JCExpression inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.maker().Unary(UNARY_OP_CODES.get(getKind()), getExpression().inline(inliner));
  }
  
  @Override
  public UExpression negate() {
    return (getKind() == Kind.LOGICAL_COMPLEMENT) ? getExpression() : super.negate();
  }
}
