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

import com.google.auto.value.AutoValue;

import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.tree.JCTree.JCExpression;

import javax.annotation.Nullable;
import javax.lang.model.type.TypeKind;

/**
 * {@link UTree} version of {@link UPrimitiveTypeTree}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class UPrimitiveTypeTree extends UExpression implements PrimitiveTypeTree {

  public static UPrimitiveTypeTree create(TypeKind kind) {
    return new AutoValue_UPrimitiveTypeTree(UPrimitiveType.create(kind));
  }
  
  abstract UPrimitiveType getPrimitiveType();
  
  public static final UPrimitiveTypeTree BYTE = create(TypeKind.BYTE);
  public static final UPrimitiveTypeTree SHORT = create(TypeKind.SHORT);
  public static final UPrimitiveTypeTree INT = create(TypeKind.INT);
  public static final UPrimitiveTypeTree LONG = create(TypeKind.LONG);
  public static final UPrimitiveTypeTree FLOAT = create(TypeKind.FLOAT);
  public static final UPrimitiveTypeTree DOUBLE = create(TypeKind.DOUBLE);
  public static final UPrimitiveTypeTree BOOLEAN = create(TypeKind.BOOLEAN);
  public static final UPrimitiveTypeTree CHAR = create(TypeKind.CHAR);
  public static final UPrimitiveTypeTree NULL = create(TypeKind.NULL);
  public static final UPrimitiveTypeTree VOID = create(TypeKind.VOID);

  @Override
  @Nullable
  public Unifier visitPrimitiveType(PrimitiveTypeTree tree, @Nullable Unifier unifier) {
    return getPrimitiveTypeKind().equals(tree.getPrimitiveTypeKind()) ? unifier : null;
  }

  @Override
  public Kind getKind() {
    return Kind.PRIMITIVE_TYPE;
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return visitor.visitPrimitiveType(this, data);
  }

  @Override
  public TypeKind getPrimitiveTypeKind() {
    return getPrimitiveType().getKind();
  }

  @Override
  public JCExpression inline(Inliner inliner) {
    return inliner.maker().Type(getPrimitiveType().inline(inliner));
  }
}
