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
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.errorprone.DescriptionListener;
import com.google.errorprone.ErrorProneScanner;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.tools.javac.util.Context;

import java.lang.annotation.Annotation;

/**
 * Wrapper around an error-prone {@code BugChecker} to use it as a {@code CodeTransformer}.
 * 
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class BugCheckerTransformer implements CodeTransformer {
  public static BugCheckerTransformer create(BugChecker checker) {
    return new AutoValue_BugCheckerTransformer(checker);
  }
  
  abstract BugChecker checker();

  @Override
  public void apply(CompilationUnitTree tree, Context context, DescriptionListener listener) {
    new ErrorProneScanner(checker()).scan(tree, new VisitorState(context, listener));
  }

  @SuppressWarnings("unchecked")
  @Override
  public ImmutableClassToInstanceMap<Annotation> annotations() {
    ImmutableClassToInstanceMap.Builder<Annotation> builder = ImmutableClassToInstanceMap.builder();
    for (Annotation annotation : checker().getClass().getDeclaredAnnotations()) {
      builder.put((Class) annotation.annotationType(), annotation);
    }
    return builder.build();
  }
}
