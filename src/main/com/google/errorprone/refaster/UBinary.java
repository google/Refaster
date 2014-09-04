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

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBinary;

import javax.annotation.Nullable;

/**
 * {@link UTree} version of {@link BinaryTree}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
abstract class UBinary extends UExpression implements BinaryTree {
  private static final ImmutableBiMap<Kind, Integer> OP_CODES =
      new ImmutableBiMap.Builder<Kind, Integer>()
          .put(Kind.PLUS, JCTree.PLUS)
          .put(Kind.MINUS, JCTree.MINUS)
          .put(Kind.MULTIPLY, JCTree.MUL)
          .put(Kind.DIVIDE, JCTree.DIV)
          .put(Kind.REMAINDER, JCTree.MOD)
          .put(Kind.LEFT_SHIFT, JCTree.SL)
          .put(Kind.RIGHT_SHIFT, JCTree.SR)
          .put(Kind.UNSIGNED_RIGHT_SHIFT, JCTree.USR)
          .put(Kind.OR, JCTree.BITOR)
          .put(Kind.AND, JCTree.BITAND)
          .put(Kind.XOR, JCTree.BITXOR)
          .put(Kind.CONDITIONAL_AND, JCTree.AND)
          .put(Kind.CONDITIONAL_OR, JCTree.OR)
          .put(Kind.LESS_THAN, JCTree.LT)
          .put(Kind.LESS_THAN_EQUAL, JCTree.LE)
          .put(Kind.GREATER_THAN, JCTree.GT)
          .put(Kind.GREATER_THAN_EQUAL, JCTree.GE)
          .put(Kind.EQUAL_TO, JCTree.EQ)
          .put(Kind.NOT_EQUAL_TO, JCTree.NE)
          .build();
  
  private static final ImmutableBiMap<Kind, Kind> NEGATION =
      new ImmutableBiMap.Builder<Kind, Kind>()
          .put(Kind.LESS_THAN, Kind.GREATER_THAN_EQUAL)
          .put(Kind.LESS_THAN_EQUAL, Kind.GREATER_THAN)
          .put(Kind.GREATER_THAN, Kind.LESS_THAN_EQUAL)
          .put(Kind.GREATER_THAN_EQUAL, Kind.LESS_THAN)
          .put(Kind.EQUAL_TO, Kind.NOT_EQUAL_TO)
          .put(Kind.NOT_EQUAL_TO, Kind.EQUAL_TO)
          .build();
  
  private static final ImmutableBiMap<Kind, Kind> DEMORGAN =
      new ImmutableBiMap.Builder<Kind, Kind>()
          .put(Kind.CONDITIONAL_AND, Kind.CONDITIONAL_OR)
          .put(Kind.CONDITIONAL_OR, Kind.CONDITIONAL_AND)
          .put(Kind.AND, Kind.OR)
          .put(Kind.OR, Kind.AND)
          .build();

  public static UBinary create(Kind binaryOp, UExpression lhs, UExpression rhs) {
    checkArgument(OP_CODES.containsKey(binaryOp), 
        "%s is not a supported binary operation", binaryOp);
    return new AutoValue_UBinary(binaryOp, lhs, rhs);
  }

  @Override
  public abstract Kind getKind();

  @Override
  public abstract UExpression getLeftOperand();

  @Override
  public abstract UExpression getRightOperand();

  @Override
  @Nullable
  public Unifier visitBinary(BinaryTree binary, @Nullable Unifier unifier) {
    unifier = getKind().equals(binary.getKind()) ? unifier : null;
    unifier = getLeftOperand().unify(binary.getLeftOperand(), unifier);
    return getRightOperand().unify(binary.getRightOperand(), unifier);
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitBinary(this, data);
  }

  @Override
  public JCBinary inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.maker().Binary(
        OP_CODES.get(getKind()), 
        getLeftOperand().inline(inliner),
        getRightOperand().inline(inliner));
  }
  
  @Override
  public UExpression negate() {
    if (NEGATION.containsKey(getKind())) {
      return UBinary.create(NEGATION.get(getKind()), getLeftOperand(), getRightOperand());
    } else if (DEMORGAN.containsKey(getKind())) {
      return UBinary.create(
          DEMORGAN.get(getKind()),
          getLeftOperand().negate(),
          getRightOperand().negate());
    } else {
      return super.negate();
    }
  }
}
