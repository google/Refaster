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
import com.google.errorprone.refaster.UTypeVar.TypeWithExpression;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree.JCExpression;

import javax.annotation.Nullable;
import javax.lang.model.element.Name;

/**
 * Identifier for a type variable in an AST; this is a syntactic representation of a
 * {@link UTypeVar}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
abstract class UTypeVarIdent extends UIdent {
  public static UTypeVarIdent create(String name) {
    return new AutoValue_UTypeVarIdent(name);
  }
  
  abstract String name();
  
  UTypeVar.Key key() {
    return new UTypeVar.Key(name());
  }

  @Override
  public JCExpression inline(Inliner inliner) {
    return inliner.getBinding(key()).inline(inliner);
  }

  @Override
  @Nullable
  protected Unifier defaultAction(Tree target, @Nullable Unifier unifier) {
    if (unifier != null) {
      JCExpression expr = (JCExpression) target;
      Type targetType = expr.type;
      @Nullable
      TypeWithExpression boundType = unifier.getBinding(key());
      if (boundType == null) {
        unifier.putBinding(key(), TypeWithExpression.create(targetType, expr));
        return unifier;
      } else if (unifier.types().isSameType(targetType, boundType.type())) {
        return unifier;
      }
    }
    return null;
  }

  @Override
  public Name getName() {
    return StringName.of(name());
  }
}
