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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.refaster.annotation.UseImportPolicy;

import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.tools.JavaFileManager;

/**
 * A representation of an entire Refaster rule, corresponding to a class with @BeforeTemplates
 * and @AfterTemplates.
 * 
 * @author lowasser@google.com (Louis Wasserman)
 * @param <M> The type of a match.
 * @param <T> The type of the template used to find matches and generate replacements.
 */
@AutoValue
public abstract class RefasterRule<M extends TemplateMatch, T extends Template<M>> 
    implements Serializable {
  public static RefasterRule<?, ?> create(String qualifiedTemplateClass,
      Collection<? extends Template<?>> beforeTemplates, @Nullable Template<?> afterTemplate) {
    return create(qualifiedTemplateClass, beforeTemplates, afterTemplate, 
        ImmutableClassToInstanceMap.<Annotation>builder().build());
  }
  
  public static RefasterRule<?, ?> create(String qualifiedTemplateClass,
      Collection<? extends Template<?>> beforeTemplates, @Nullable Template<?> afterTemplate,
      ImmutableClassToInstanceMap<Annotation> annotations) {

    checkState(!beforeTemplates.isEmpty(),
        "No @BeforeTemplate was found in the specified class: %s", qualifiedTemplateClass);
    Class<?> templateType = beforeTemplates.iterator().next().getClass();
    for (Template<?> beforeTemplate : beforeTemplates) {
      checkState(beforeTemplate.getClass().equals(templateType),
          "Expected all templates to be of type %s but found template of type %s in %s",
          templateType, beforeTemplate.getClass(), qualifiedTemplateClass);
    }
    if (afterTemplate != null) {
      checkState(afterTemplate.getClass().equals(templateType),
          "Expected all templates to be of type %s but found template of type %s in %s",
          templateType, afterTemplate.getClass(), qualifiedTemplateClass);
    }
    @SuppressWarnings("unchecked")
    RefasterRule<?, ?> result = new AutoValue_RefasterRule(
        qualifiedTemplateClass, ImmutableList.copyOf(beforeTemplates), afterTemplate, annotations);
    return result;
  }
  
  RefasterRule() {}
  
  abstract String qualifiedTemplateClass();
  abstract ImmutableList<T> beforeTemplates();
  @Nullable abstract T afterTemplate();
  public abstract ImmutableClassToInstanceMap<Annotation> annotations();
  
  public ImportPolicy importPolicy() {
    if (afterTemplate() != null) {
      UseImportPolicy importPolicy = afterTemplate().annotations()
          .getInstance(UseImportPolicy.class);
      if (importPolicy != null) {
        return importPolicy.value();
      }
    }
    return ImportPolicy.IMPORT_TOP_LEVEL;
  }
  
  boolean rejectMatchesWithComments() {
    return true; // TODO(lowasser): worth making configurable?
  }
  
  public Context prepareContext(Context baseContext, JCCompilationUnit compilationUnit) {
    Context context = new SubContext(baseContext);
    if (context.get(JavaFileManager.class) == null) {
      JavacFileManager.preRegister(context);
    }
    ImportPolicy.bind(context, importPolicy());
    context.put(JCCompilationUnit.class, compilationUnit);
    context.put(PackageSymbol.class, compilationUnit.packge);
    return context;
  }

  @Override
  public String toString() {
    List<String> path = Splitter.on('.').splitToList(qualifiedTemplateClass());
    for (int topLevel = 0; topLevel < path.size() - 1; topLevel++) {
      if (Ascii.isUpperCase(path.get(topLevel).charAt(0))) {
        return Joiner.on('_').join(path.subList(topLevel + 1, path.size()));
      }
    }
    return qualifiedTemplateClass();
  }
}
