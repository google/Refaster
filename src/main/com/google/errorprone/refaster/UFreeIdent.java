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
import com.google.common.collect.Iterables;
import com.google.errorprone.util.ASTHelpers;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.util.Names;

import javax.annotation.Nullable;
import javax.lang.model.element.Name;

/**
 * Free identifier that can be bound to any expression of the appropriate type.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class UFreeIdent extends UIdent {  
  public static class Key extends Bindings.Key<JCExpression> {
    public Key(String name) {
      super(name);
    }
  }
  
  public static UFreeIdent create(String identifier) {
    return new AutoValue_UFreeIdent(identifier);
  }
  
  abstract String identifier();
  
  public Key key() {
    return new Key(identifier());
  }

  @Override
  public JCExpression inline(Inliner inliner) {
    return inliner.getBinding(key());
  }
  
  static boolean trueOrNull(@Nullable Boolean condition) {
    return condition == null || condition;
  }

  @Override
  @Nullable
  public Unifier unify(JCTree target, @Nullable final Unifier unifier) {
    if (unifier != null && target instanceof JCExpression) {
      JCExpression expression = (JCExpression) target;
      
      Names names = Names.instance(unifier.getContext());
      if (expression instanceof JCIdent && ((JCIdent) expression).name.equals(names._super)) {
        return null;
      }
      
      JCExpression currentBinding = unifier.getBinding(key());
      
      // Check that the expression does not reference any template-local variables.
      boolean isGood = trueOrNull(new TreeScanner<Boolean, Void>() {
        @Override
        public Boolean reduce(Boolean left, Boolean right) {
          return trueOrNull(left) && trueOrNull(right);
        }

        @Override
        public Boolean visitIdentifier(IdentifierTree ident, Void v) {
          for (ULocalVarIdent.Key key : 
              Iterables.filter(unifier.getBindings().keySet(), ULocalVarIdent.Key.class)) {
            if (unifier.getBinding(key).getSymbol() == ASTHelpers.getSymbol(ident)) {
              return false;
            }
          }
          return true;
        }
      }.scan(expression, null));
      if (!isGood) {
        return null;
      } else if (currentBinding == null) {
        unifier.putBinding(key(), expression);
        return unifier;
      } else if (unifier.types().isSameType(currentBinding.type, expression.type)
          && currentBinding.toString().equals(expression.toString())) {
        // If it's the same type and the same code, treat it as the same expression.
        return unifier;
      }
    }
    return null;
  }

  @Override
  public Name getName() {
    // we don't have a Context, so use StringName
    return StringName.of(identifier());
  }
}
