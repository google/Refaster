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
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.fixes.Fix;
import com.google.errorprone.fixes.SuggestedFix;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Warner;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Template representing a sequence of consecutive statements.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
public abstract class BlockTemplate extends Template<BlockTemplateMatch> {
  private static final Logger logger = Logger.getLogger(BlockTemplate.class.toString());
  
  public static BlockTemplate create(UStatement... templateStatements) {
    return create(ImmutableMap.<String, UType>of(), templateStatements);
  }
  
  public static BlockTemplate create(
      Map<String, ? extends UType> expressionArgumentTypes, UStatement... templateStatements) {
    return create(ImmutableList.<UTypeVar>of(), expressionArgumentTypes, templateStatements);
  }
  
  public static BlockTemplate create(
      Iterable<UTypeVar> typeVariables,
      Map<String, ? extends UType> expressionArgumentTypes,
      UStatement... templateStatements) {
    return create(
        ImmutableClassToInstanceMap.<Annotation>builder().build(), 
        typeVariables, expressionArgumentTypes, ImmutableList.copyOf(templateStatements));
  }
  
  public static BlockTemplate create(
      ImmutableClassToInstanceMap<Annotation> annotations,
      Iterable<UTypeVar> typeVariables,
      Map<String, ? extends UType> expressionArgumentTypes,
      Iterable<? extends UStatement> templateStatements) {
    return new AutoValue_BlockTemplate(
        annotations,
        ImmutableList.copyOf(typeVariables),
        ImmutableMap.copyOf(expressionArgumentTypes),
        ImmutableList.copyOf(templateStatements));        
  }
  
  abstract ImmutableList<UStatement> templateStatements();      

  /**
   * If the tree is a {@link JCBlock}, returns a list of disjoint matches corresponding to
   * the exact list of template statements found consecutively; otherwise, returns an
   * empty list.
   */
  @Override
  public Iterable<BlockTemplateMatch> match(JCTree tree, Context context) {
    // TODO(lowasser): consider nonconsecutive matches?
    if (tree instanceof JCBlock) {
      JCBlock block = (JCBlock) tree;
      List<JCStatement> targetStatements = ImmutableList.copyOf(block.getStatements());
      ImmutableList.Builder<BlockTemplateMatch> builder = ImmutableList.builder();
      for (int start = 0; start + templateStatements().size() <= targetStatements.size(); 
          start++) {
        int end = start + templateStatements().size();
        Unifier unifier = match(targetStatements.subList(start, end), context);
        if (unifier != null) {
          builder.add(new BlockTemplateMatch(block, unifier, start, end));
          start = end - 1;
        }
      }
      return builder.build();
    }
    return ImmutableList.of();
  }

  @Nullable
  private Unifier match(List<JCStatement> targetStatements, Context context) {
    checkArgument(templateStatements().size() == targetStatements.size());
    Unifier unifier = new Unifier(context);
    for (int i = 0; i < templateStatements().size() && unifier != null; i++) {
      unifier = templateStatements().get(i).unify(targetStatements.get(i), unifier);
    }
    if (unifier != null) {
      Inliner inliner = unifier.createInliner();
      try {
        return typecheck(
            unifier, inliner, new Warner(targetStatements.get(0)), expectedTypes(inliner),
            actualTypes(inliner));
      } catch (CouldNotResolveImportException e) {
        logger.log(FINE, "Failure to resolve import", e);
      }
    }
    return null;
  }
  
  /**
   * Returns a {@code String} representation of a statement, including semicolon.
   */
  private static String printStatement(Context context, JCStatement statement) {
    StringWriter writer = new StringWriter();
    try {
      pretty(context, writer).printStat(statement);
    } catch (IOException e) {
      throw new AssertionError("StringWriter cannot throw IOExceptions");
    }
    return writer.toString();    
  }
  
  /**
   * Returns a {@code String} representation of a sequence of statements,
   * with semicolons and newlines.
   */
  private static String printStatements(
      Context context, com.sun.tools.javac.util.List<JCStatement> statements) {
    StringWriter writer = new StringWriter();
    try {
      pretty(context, writer).printStats(statements);
    } catch (IOException e) {
      throw new AssertionError("StringWriter cannot throw IOExceptions");
    }
    return writer.toString(); 
  }
  
  @Override
  public Fix replace(BlockTemplateMatch match) {
    checkNotNull(match);
    SuggestedFix.Builder fix = SuggestedFix.builder();
    Inliner inliner = match.createInliner();
    Context context = inliner.getContext();
    List<JCStatement> targetStatements = match.getStatements();
    try {
      int nTemplates = templateStatements().size();
      int nTargets = targetStatements.size();
      if (nTemplates <= nTargets) {
        for (int i = 0; i < nTemplates; i++) {
          fix.replace(
              targetStatements.get(i), 
              printStatement(context, templateStatements().get(i).inline(inliner)));
        }
        for (int i = templateStatements().size(); i < nTargets; i++) {
          fix.delete(targetStatements.get(i));
        }
      } else {
        for (int i = 0; i < nTargets - 1; i++) {
          fix.replace(
              targetStatements.get(i),
              printStatement(context, templateStatements().get(i).inline(inliner)));
        }
        int last = nTargets - 1;
        ImmutableList<UStatement> remainingTemplate = 
            templateStatements().subList(last, templateStatements().size());
        fix.replace(targetStatements.get(last), 
            printStatements(context, inliner.inlineList(remainingTemplate)));
      }
    } catch (CouldNotResolveImportException e) {
      logger.log(SEVERE, "Failure to resolve import in replacement", e);
    }
    return addImports(inliner, fix);
  }
}
