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

import static java.util.logging.Level.FINE;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.fixes.Fix;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.refaster.UTypeVar.TypeWithExpression;
import com.google.errorprone.refaster.annotation.NoAutoboxing;

import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Infer.InferenceException;
import com.sun.tools.javac.comp.Infer.NoInstanceException;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.Pretty;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Warner;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Abstract superclass for templates that can be used to search and replace in a Java syntax tree.
 * 
 * @author lowasser@google.com (Louis Wasserman)
 * @param <M> Type of a match for this template.
 */
public abstract class Template<M extends TemplateMatch> implements Serializable {
  private static final Logger logger = Logger.getLogger(Template.class.toString());

  public static final boolean AUTOBOXING_DEFAULT = true;

  public abstract ImmutableClassToInstanceMap<Annotation> annotations();
  public abstract ImmutableList<UTypeVar> typeVariables();
  public abstract ImmutableMap<String, UType> expressionArgumentTypes();
  
  public abstract Iterable<M> match(JCTree tree, Context context);
  public abstract Fix replace(M match);
  
  boolean autoboxing() {
    return !annotations().containsKey(NoAutoboxing.class);
  }

  /**
   * Returns a list of the expected types of the expression arguments, in order. 
   * (This is equivalent to the list of argument types of the @BeforeTemplate method.)
   *
   * @throws CouldNotResolveImportException if a referenced type could not be resolved
   */
  protected List<Type> expectedTypes(Inliner inliner) throws CouldNotResolveImportException {
    Type[] result = new Type[expressionArgumentTypes().size()];
    ImmutableList<UType> types = expressionArgumentTypes().values().asList();
    ImmutableList<String> argNames = expressionArgumentTypes().keySet().asList();
    for (int i = 0; i < result.length; i++) {
      String argName = argNames.get(i);
      Optional<JCExpression> singleBinding =
          inliner.getOptionalBinding(new UFreeIdent.Key(argName));
      if (!singleBinding.isPresent()) {
        Optional<java.util.List<JCExpression>> exprs =
            inliner.getOptionalBinding(new URepeated.Key(argName));
        if (!exprs.isPresent() || exprs.get().isEmpty()) {
          // It is a repeated template variable and matches no expressions.
          continue;
        }
      }
      result[i] = types.get(i).inline(inliner);
    }
    return List.from(result);
  }

  /**
   * Returns a list of the actual types of the expression arguments, in order. (This expects the
   * expression arguments to have already been captured, and for the {@link Inliner} to contain
   * those bindings.)
   */
  protected List<Type> actualTypes(Inliner inliner) {
    Type[] result = new Type[expressionArgumentTypes().size()];
    ImmutableList<String> argNames = expressionArgumentTypes().keySet().asList();
    for (int i = 0; i < result.length; i++) {
      String argName = argNames.get(i);
      Optional<JCExpression> singleBinding =
          inliner.getOptionalBinding(new UFreeIdent.Key(argName));
      if (singleBinding.isPresent()) {
        result[i] = singleBinding.get().type;
      } else {
        Optional<java.util.List<JCExpression>> exprs =
            inliner.getOptionalBinding(new URepeated.Key(argName));
        if (exprs.isPresent() && !exprs.get().isEmpty()) {
          // The argument matches 1 or more expressions.
          Type[] exprTys = new Type[exprs.get().size()];
          for (int j = 0; j < exprs.get().size(); j++) {
            exprTys[j] = exprs.get().get(j).type;
          }
          // Get the least upper bound of the types of all expressions that the argument matches.
          result[i] = inliner.types().lub(List.from(exprTys));
        }
      }
    }
    return List.from(result);
  }

  @Nullable
  protected Unifier typecheck(Unifier unifier, Inliner inliner, Warner warner,
      List<Type> expectedTypes, List<Type> actualTypes) {
    try {
      ImmutableList<UTypeVar> freeTypeVars = freeTypeVars(unifier);
      infer(warner,
          inliner,
          inliner.<Type, UTypeVar>inlineList(freeTypeVars),
          expectedTypes,
          inliner.symtab().voidType,
          actualTypes);

      for (UTypeVar var : freeTypeVars) {
        Type instantiationForVar = infer(warner,
            inliner,
            inliner.<Type, UTypeVar>inlineList(freeTypeVars),
            expectedTypes,
            var.inline(inliner),
            actualTypes);
        unifier.putBinding(var.key(), 
            TypeWithExpression.create(instantiationForVar.getReturnType()));
      }
      
      if (!checkBounds(unifier, inliner, warner)) {
        return null;
      }
      return unifier;
    } catch (CouldNotResolveImportException e) {
      logger.log(FINE, "Failure to resolve an import", e);
      return null;
    } catch (InferenceException e) {
      logger.log(FINE, "No valid instantiation found: " + e.getDiagnostic());
      return null;
    }
  }
  
  private boolean checkBounds(Unifier unifier, Inliner inliner, Warner warner)
      throws CouldNotResolveImportException {
    Types types = unifier.types();
    ListBuffer<Type> varsBuffer = ListBuffer.lb();
    ListBuffer<Type> bindingsBuffer = ListBuffer.lb();
    for (UTypeVar typeVar : typeVariables()) {
      varsBuffer.add(inliner.inlineAsVar(typeVar));
      bindingsBuffer.add(unifier.getBinding(typeVar.key()).type());
    }
    List<Type> vars = varsBuffer.toList();
    List<Type> bindings = bindingsBuffer.toList();
    for (UTypeVar typeVar : typeVariables()) {
      List<Type> bounds = types.getBounds(inliner.inlineAsVar(typeVar));
      bounds = types.subst(bounds, vars, bindings);
      if (!types.isSubtypeUnchecked(unifier.getBinding(typeVar.key()).type(), bounds, warner)) {
        logger.log(FINE,
            String.format("%s is not a subtype of %s", inliner.getBinding(typeVar.key()), bounds));
        return false;
      }
    }
    return true;
  }
  
  protected static Pretty pretty(Context context, final Writer writer) {
    final JCCompilationUnit unit = context.get(JCCompilationUnit.class);
    try {
      final String unitContents = unit.getSourceFile().getCharContent(false).toString();
      return new Pretty(writer, true) {
        @Override
        public void visitAnnotation(JCAnnotation anno) {
          if (anno.getArguments().isEmpty()) {
            try {
              print("@");
              printExpr(anno.annotationType);
            } catch (IOException e) {
              // the supertype swallows exceptions too
              throw new RuntimeException(e);
            }
          } else {
            super.visitAnnotation(anno);
          }
        }

        @Override
        public void printExpr(JCTree tree, int prec) throws IOException {
          Map<JCTree, Integer> endPositions = unit.endPositions;
          /* 
           * Modifiers, and specifically flags like final, appear to just need weird special 
           * handling.
           */
          if (tree.getKind() != Kind.MODIFIERS && endPositions.containsKey(tree)) {
            writer.append(unitContents.substring(
                tree.getStartPosition(), tree.getEndPosition(endPositions)));
          } else {
            super.printExpr(tree, prec);
          }
        }
      };
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the inferred method type of the template based on the given actual argument types.
   *
   * @throws NoInstanceException if no instances of the specified type variables would allow the
   *         {@code actualArgTypes} to match the {@code expectedArgTypes}
   */
  private Type infer(Warner warner,
      Inliner inliner,
      List<Type> freeTypeVariables,
      List<Type> expectedArgTypes,
      Type returnType,
      List<Type> actualArgTypes) throws NoInstanceException {
    Symtab symtab = inliner.symtab();
    MethodType methodType =
        new MethodType(expectedArgTypes, returnType, List.<Type>nil(), symtab.methodClass);
    Enter enter = inliner.enter();
    MethodSymbol methodSymbol =
        new MethodSymbol(0, inliner.asName("__m__"), methodType, symtab.unknownSymbol);
    return inliner.infer().instantiateMethod(enter.getEnv(methodType.tsym),
        freeTypeVariables,
        methodType,
        methodSymbol,
        actualArgTypes,
        autoboxing(),
        false,
        warner);
  }

  /**
   * Returns a list of the elements of {@code typeVariables} that are <em>not</em> bound in the
   * specified {@link Unifier}.
   */
  private ImmutableList<UTypeVar> freeTypeVars(Unifier unifier) {
    ImmutableList.Builder<UTypeVar> builder = ImmutableList.builder();
    for (UTypeVar var : typeVariables()) {
      if (unifier.getBinding(var.key()) == null) {
        builder.add(var);
      }
    }
    return builder.build();
  }

  protected static Fix addImports(Inliner inliner, SuggestedFix.Builder fix) {
    for (String importToAdd : inliner.getImportsToAdd()) {
      fix.addImport(importToAdd);
    }
    for (String staticImportToAdd : inliner.getStaticImportsToAdd()) {
      fix.addStaticImport(staticImportToAdd);
    }
    return fix.build();
  }
}
