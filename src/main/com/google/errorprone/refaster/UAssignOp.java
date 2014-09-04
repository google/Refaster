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

import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAssignOp;

import javax.annotation.Nullable;

/**
 * {@link UTree} representation of a {@link CompoundAssignmentTree}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class UAssignOp extends UExpression implements CompoundAssignmentTree {
  private static final ImmutableBiMap<Kind, Integer> TAG = ImmutableBiMap.<Kind, Integer>builder()
      .put(Kind.PLUS_ASSIGNMENT, JCTree.PLUS_ASG)
      .put(Kind.MINUS_ASSIGNMENT, JCTree.MINUS_ASG)
      .put(Kind.MULTIPLY_ASSIGNMENT, JCTree.MUL_ASG)
      .put(Kind.DIVIDE_ASSIGNMENT, JCTree.DIV_ASG)
      .put(Kind.REMAINDER_ASSIGNMENT, JCTree.MOD_ASG)
      .put(Kind.LEFT_SHIFT_ASSIGNMENT, JCTree.SL_ASG)
      .put(Kind.RIGHT_SHIFT_ASSIGNMENT, JCTree.SR_ASG)
      .put(Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT, JCTree.USR_ASG)
      .put(Kind.OR_ASSIGNMENT, JCTree.BITOR_ASG)
      .put(Kind.AND_ASSIGNMENT, JCTree.BITAND_ASG)
      .put(Kind.XOR_ASSIGNMENT, JCTree.BITXOR_ASG)
      .build();

  public static UAssignOp create(UExpression variable, Kind operator, UExpression expression) {
    checkArgument(TAG.containsKey(operator),
        "Tree kind %s does not represent a compound assignment operator", operator);
    return new AutoValue_UAssignOp(variable, operator, expression);
  }

  @Override
  public abstract UExpression getVariable();

  @Override
  public abstract Kind getKind();

  @Override
  public abstract UExpression getExpression();

  @Override
  public JCAssignOp inline(Inliner inliner) throws CouldNotResolveImportException {
    return inliner.maker().Assignop(
        TAG.get(getKind()), getVariable().inline(inliner), getExpression().inline(inliner));
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitCompoundAssignment(this, data);
  }

  // TODO(lowasser): consider matching x = x ? y as well as x ?= y
  
  @Override
  @Nullable
  public Unifier visitCompoundAssignment(
      CompoundAssignmentTree assignOp, @Nullable Unifier unifier) {
    unifier = (getKind() == assignOp.getKind()) ? unifier : null;
    unifier = getVariable().unify(assignOp.getVariable(), unifier);
    return getExpression().unify(assignOp.getExpression(), unifier);      
  }
}
