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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.refaster.Bindings.Key;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A mutable representation of an attempt to match a template source tree against a target source
 * tree.
 *
 * @author Louis Wasserman
 */
public final class Unifier {
  private final Bindings bindings;
  
  private final Context context;

  public Unifier(Context context) {
    this.bindings = Bindings.create();
    this.context = checkNotNull(context);
  }
  
  private Unifier(Context context, Bindings bindings) {
    this.context = new SubContext(context);
    this.bindings = Bindings.create(bindings);
  }
  
  /**
   * Returns a {@code Unifier} containing all the bindings from this {@code Unifier},
   * but which can succeed or fail independently of this {@code Unifier}.
   */
  public Unifier fork() {
    return new Unifier(context, bindings);
  }
  
  public Types types() {
    return Types.instance(context);
  }
  
  public JCExpression thisExpression(Type type) {
    return TreeMaker.instance(context).This(type);
  }
  
  public Inliner createInliner() {
    return new Inliner(context, bindings);
  }
  
  @Nullable
  public <V> V getBinding(Key<V> key) {
    return bindings.getBinding(key);
  }

  public <V> V putBinding(Key<V> key, V value) {
    checkArgument(!bindings.containsKey(key), "Cannot bind %s more than once", key);
    return bindings.putBinding(key, value);
  }

  public <V> V replaceBinding(Key<V> key, V value) {
    checkArgument(bindings.containsKey(key), "Binding for %s does not exist", key);
    return bindings.putBinding(key, value);
  }
  
  /**
   * Attempts to unify each each element of {@code toUnify} with the corresponding
   * element of {@code targets}.
   */
  public static <T, U extends Unifiable<? super T>> Unifier unifyList(
      @Nullable Unifier unifier, @Nullable List<U> toUnify, @Nullable List<T> targets) {
    return unifyList(unifier, toUnify, targets, false);
  }

  public static <T, U extends Unifiable<? super T>> Unifier unifyList(
      @Nullable Unifier unifier, @Nullable List<U> toUnify,
      @Nullable List<T> targets, boolean allowVarargs) {
    if (toUnify == null) {
      return (targets == null) ? unifier : null;
    } else if (targets == null || !allowVarargs && toUnify.size() != targets.size()) {
      return null;
    }
    Iterator<U> toUnifyItr = toUnify.iterator();
    Iterator<? extends T> targetItr = targets.iterator();
    while (unifier != null && toUnifyItr.hasNext()) {
      U toUnifyNext = toUnifyItr.next();
      if (allowVarargs && toUnifyNext instanceof URepeated) {
        URepeated repeated = (URepeated) toUnifyNext;
        if (toUnifyItr.hasNext()) {
          // There are more to unify after repeated.
          return null;
        }
        return unifyRepeated(unifier, repeated, targetItr);
      } else {
        unifier = toUnifyNext.unify(targetItr.next(), unifier);
      }
    }
    return unifier;
  }

  // Attempts to unify a repeated template variable with a sequence of targets.
  // Binds the key of repeated to a list of expressions if the unification succeeds.
  private static <T, U extends Unifiable<? super T>> Unifier unifyRepeated (
      Unifier unifier, URepeated repeated, Iterator<? extends T> targetItr) {
    List<JCExpression> expressions = new ArrayList<>();
    while (targetItr.hasNext()) {
      // Unification of each target uses a fork of unifier, since one key can be bound only once.
      Unifier forked = unifier.fork();
      forked = repeated.unify((JCTree) (targetItr.next()), forked);
      JCExpression boundExpr = repeated.getUnderlyingBinding(forked);
      if (boundExpr == null) {
        return null;
      }
      expressions.add(boundExpr);
    }
    unifier.putBinding(repeated.key(), expressions);
    return unifier;
  }

  /**
   * Attempts to unify a nullable template with a nullable target.
   */
  public static <T, U extends Unifiable<? super T>> Unifier unifyNullable(
      @Nullable Unifier unifier, @Nullable U toUnify, @Nullable T target) {
    if (toUnify == null && target == null) {
      return unifier;
    } else if (unifier != null && toUnify != null && target != null) {
      return toUnify.unify(target, unifier);
    } else {
      return null;
    }
  }

  public Bindings getBindings() {
    return bindings.snapshot();
  }

  public Context getContext() {
    return context;
  }
}
