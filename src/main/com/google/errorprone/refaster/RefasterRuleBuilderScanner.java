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
import static java.util.logging.Level.FINE;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.util.ASTHelpers;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.util.Context;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 * Scanner implementation to extract a single Refaster rule from a {@code ClassTree}.
 * 
 * @author lowasser@google.com (Louis Wasserman)
 */
public final class RefasterRuleBuilderScanner extends SimpleTreeVisitor<Void, Void> {
  private static final Logger logger =
      Logger.getLogger(RefasterRuleBuilderScanner.class.toString());
  
  private final Context context;
  private final List<Template<?>> beforeTemplates;
  private Template<?> afterTemplate;

  private RefasterRuleBuilderScanner(Context context) {
    this.context = new SubContext(context);
    this.beforeTemplates = new ArrayList<>();
    this.afterTemplate = null;
  }
  
  public static Collection<? extends RefasterRule<?, ?>> extractRules(ClassTree tree,
      Context context) {
    ClassSymbol sym = ASTHelpers.getSymbol(tree);
    
    RefasterRuleBuilderScanner scanner = new RefasterRuleBuilderScanner(context);
    scanner.visit(tree.getMembers(), null);
    return scanner.createMatchers(
        sym.getQualifiedName().toString(),
        UTemplater.annotationMap(sym));
  }

  @Override
  public Void visitMethod(MethodTree tree, Void v) {
    try {
      logger.log(FINE, "Discovered method with name {0}", tree.getName());
      if (ASTHelpers.hasAnnotation(tree, BeforeTemplate.class)) {
        checkState(afterTemplate == null, "BeforeTemplate must come before AfterTemplate");
        Template<?> template = UTemplater.createTemplate(context, tree);
        beforeTemplates.add(template);
        if (template instanceof BlockTemplate) {
          context.put(UTemplater.REQUIRE_BLOCK_KEY, true);
        }
        logger.log(FINE, "Before method template: {0}", template);
      } else if (ASTHelpers.hasAnnotation(tree, AfterTemplate.class)) {
        checkState(afterTemplate == null, "Multiple after methods not currently supported");
        afterTemplate = UTemplater.createTemplate(context, tree);
        logger.log(FINE, "After method template: {0}", afterTemplate);
      }
      return null;
    } catch (Throwable t) {
      throw new RuntimeException("Error analysing: " + tree.getName(), t);
    }
  }

  private Collection<? extends RefasterRule<?, ?>> createMatchers(
      String qualifiedTemplateClass, ImmutableClassToInstanceMap<Annotation> annotationMap) {
    if (beforeTemplates.isEmpty() && afterTemplate == null) {
      // there's no template here
      return ImmutableList.of();
    } else {
      RefasterRule<?, ?> rule = RefasterRule.create(qualifiedTemplateClass,
          beforeTemplates, afterTemplate, annotationMap);
      if (afterTemplate instanceof ExpressionTemplate 
          && ((ExpressionTemplate) afterTemplate).generateNegation()) {
        List<ExpressionTemplate> negatedBeforeTemplates = new ArrayList<>();
        for (Template<?> beforeTemplate : beforeTemplates) {
          negatedBeforeTemplates.add(((ExpressionTemplate) beforeTemplate).negation());
        }
        RefasterRule<?, ?> negation = RefasterRule.create(
            qualifiedTemplateClass, negatedBeforeTemplates, 
            ((ExpressionTemplate) afterTemplate).negation(), annotationMap);
        return ImmutableList.of(rule, negation);
      }
      return ImmutableList.of(rule);
    }
  }

}
