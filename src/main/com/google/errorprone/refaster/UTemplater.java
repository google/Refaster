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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.refaster.annotation.AlsoReverseTernary;
import com.google.errorprone.refaster.annotation.Matches;
import com.google.errorprone.refaster.annotation.NotMatches;
import com.google.errorprone.refaster.annotation.OfKind;
import com.google.errorprone.refaster.annotation.Repeated;
import com.google.errorprone.util.ASTHelpers;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.code.Attribute.Compound;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ArrayType;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.ForAll;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.model.AnnotationProxyMaker;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCArrayAccess;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCAssignOp;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCInstanceOf;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCNewArray;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCSkip;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSynchronized;
import com.sun.tools.javac.tree.JCTree.JCThrow;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import com.sun.tools.javac.util.Context;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;

/**
 * Converts a type-checked syntax tree to a portable {@code UTree} template.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
public class UTemplater {
  /**
   * Context key to indicate that templates should be treated as BlockTemplates, regardless
   * of their structure.
   */
  public static final Context.Key<Boolean> REQUIRE_BLOCK_KEY = new Context.Key<>();
  
  /**
   * Returns a template based on a method. One-line methods starting with a {@code return} statement
   * are guessed to be expression templates, and all other methods are guessed to be block
   * templates.
   */
  public static Template<?> createTemplate(Context context, JCMethodDecl decl) {
    ImmutableClassToInstanceMap<Annotation> annotations = 
        UTemplater.annotationMap(decl.sym);
    ImmutableMap<String, VarSymbol> freeExpressionVars = freeExpressionVariables(decl);
    Context subContext = new SubContext(context);
    if (annotations.containsKey(AlsoReverseTernary.class)) {
      subContext.put(AlsoReverseTernary.class, annotations.getInstance(AlsoReverseTernary.class));
    }
    final UTemplater templater = new UTemplater(freeExpressionVars, subContext);
    ImmutableMap<String, UType> expressionVarTypes = ImmutableMap.copyOf(
        Maps.transformValues(freeExpressionVars, new Function<VarSymbol, UType>() {
          @Override
          public UType apply(VarSymbol sym) {
            return templater.template(sym.type);
          }
        }));

    UType genericType = templater.template(decl.sym.type);
    List<UTypeVar> typeParameters;
    UMethodType methodType;
    if (genericType instanceof UForAll) {
      UForAll forAllType = (UForAll) genericType;
      typeParameters = forAllType.getTypeVars();
      methodType = (UMethodType) forAllType.getQuantifiedType();
    } else if (genericType instanceof UMethodType) {
      typeParameters = ImmutableList.of();
      methodType = (UMethodType) genericType;
    } else {
      throw new IllegalArgumentException(
          "Expected genericType to be either a ForAll or a UMethodType, but was " + genericType);
    }

    List<JCStatement> bodyStatements = decl.getBody().getStatements();
    if (bodyStatements.size() == 1
        && Iterables.getOnlyElement(bodyStatements).getKind() == Kind.RETURN
        && context.get(REQUIRE_BLOCK_KEY) == null) {
      JCExpression expression =
          ((JCReturn) Iterables.getOnlyElement(bodyStatements)).getExpression();
      return ExpressionTemplate.create(
          annotations, typeParameters, expressionVarTypes,
          templater.template(expression), methodType.getReturnType());
    } else {
      List<UStatement> templateStatements = new ArrayList<>();
      for (JCStatement statement : bodyStatements) {
        templateStatements.add(templater.template(statement));
      }
      return BlockTemplate.create(annotations, 
          typeParameters, expressionVarTypes, templateStatements);
    }
  }

  public static ImmutableMap<String, VarSymbol> freeExpressionVariables(
      JCMethodDecl templateMethodDecl) {
    ImmutableMap.Builder<String, VarSymbol> builder = ImmutableMap.builder();
    for (JCVariableDecl param : templateMethodDecl.getParameters()) {
      builder.put(param.getName().toString(), param.sym);
    }
    return builder.build();
  }

  private final ImmutableMap<String, VarSymbol> freeVariables;
  private final Map<TypeSymbol, UTypeVar> typeVariables;
  private final Context context;

  public UTemplater(Map<String, VarSymbol> freeVariables, Context context) {
    this.freeVariables = ImmutableMap.copyOf(freeVariables);
    this.typeVariables = Maps.newHashMap();
    this.context = context;
  }

  UTemplater(Context context) {
    this(ImmutableMap.<String, VarSymbol>of(), context);
  }

  public UTree<?> template(JCTree tree) {
    if (tree instanceof JCExpression) {
      return template((JCExpression) tree);
    } else if (tree instanceof JCStatement) {
      return template((JCStatement) tree);
    } else if (tree instanceof JCMethodDecl) {
      return template((JCMethodDecl) tree);
    } else if (tree instanceof JCModifiers) {
      return template((JCModifiers) tree);
    } else {
      throw new IllegalArgumentException(
          "Refaster does not currently support syntax " + tree.getClass());
    }
  }
  
  private UMethodDecl template(JCMethodDecl decl) {
    return UMethodDecl.create(
        template(decl.getModifiers()),
        decl.getName().toString(),
        template((JCExpression) decl.getReturnType()),
        Iterables.filter(templateStatements(decl.getParameters()), UVariableDecl.class),
        templateExpressions(decl.getThrows()),
        template(decl.getBody()));
  }
  
  private UModifiers template(JCModifiers modifiers) {
    return UModifiers.create(modifiers.flags,
        Iterables.filter(templateExpressions(modifiers.getAnnotations()), UAnnotation.class));
  }

  public UExpression template(JCExpression tree) {
    if (tree instanceof JCArrayTypeTree) {
      return template((JCArrayTypeTree) tree);
    } else if (tree instanceof JCTypeApply) {
      return template((JCTypeApply) tree);
    } else if (tree instanceof JCPrimitiveTypeTree) {
      return template((JCPrimitiveTypeTree) tree);
    } else if (tree instanceof JCLiteral) {
      return template((JCLiteral) tree);
    } else if (tree instanceof JCParens) {
      return template((JCParens) tree);
    } else if (tree instanceof JCFieldAccess) {
      return template((JCFieldAccess) tree);
    } else if (tree instanceof JCArrayAccess) {
      return template((JCArrayAccess) tree);
    } else if (tree instanceof JCAssign) {
      return template((JCAssign) tree);
    } else if (tree instanceof JCAnnotation) {
      return template((JCAnnotation) tree);
    } else if (tree instanceof JCNewArray) {
      return template((JCNewArray) tree);
    } else if (tree instanceof JCNewClass) {
      return template((JCNewClass) tree);
    } else if (tree instanceof JCInstanceOf) {
      return template((JCInstanceOf) tree);
    } else if (tree instanceof JCIdent) {
      return template((JCIdent) tree);
    } else if (tree instanceof JCMethodInvocation) {
      return template((JCMethodInvocation) tree);
    } else if (tree instanceof JCBinary) {
      return template((JCBinary) tree);
    } else if (tree instanceof JCAssignOp) {
      return template((JCAssignOp) tree);
    } else if (tree instanceof JCConditional) {
      return template((JCConditional) tree);
    } else if (tree instanceof JCIdent) {
      return template((JCIdent) tree);
    } else if (tree instanceof JCUnary) {
      return template((JCUnary) tree);
    } else if (tree instanceof JCTypeCast) {
      return template((JCTypeCast) tree);
    } else {
      throw new IllegalArgumentException(
          "Refaster does not currently support syntax " + tree.getClass());
    }
  }

  @Nullable
  private List<UExpression> templateExpressions(
      @Nullable List<? extends JCExpression> expressions) {
    if (expressions == null) {
      return null;
    }
    ImmutableList.Builder<UExpression> builder = ImmutableList.builder();
    for (JCExpression expression : expressions) {
      builder.add(template(expression));
    }
    return builder.build();
  }

  private UInstanceOf template(JCInstanceOf tree) {
    return UInstanceOf.create(template(tree.getExpression()), template(tree.getType()));
  }

  private UPrimitiveTypeTree template(JCPrimitiveTypeTree tree) {
    return UPrimitiveTypeTree.create(tree.getPrimitiveTypeKind());
  }

  private ULiteral template(JCLiteral tree) {
    return ULiteral.create(tree.getKind(), tree.getValue());
  }

  private UParens template(JCParens tree) {
    return UParens.create(template(tree.getExpression()));
  }

  private UAssign template(JCAssign tree) {
    return UAssign.create(template(tree.getVariable()), template(tree.getExpression()));
  }

  private UArrayAccess template(JCArrayAccess tree) {
    return UArrayAccess.create(template(tree.getExpression()), template(tree.getIndex()));
  }

  private UAnnotation template(JCAnnotation tree) {
    return UAnnotation.create(
        template(tree.getAnnotationType()), templateExpressions(tree.getArguments()));
  }

  private UExpression template(JCFieldAccess tree) {
    if (tree.sym instanceof ClassSymbol) {
      return UClassIdent.create((ClassSymbol) tree.sym);
    } else if (tree.sym.isStatic()) {
      JCExpression selected = tree.getExpression();
      checkState(ASTHelpers.getSymbol(selected) instanceof ClassSymbol,
          "Refaster cannot match static methods used on instances");
      return staticMember(tree.sym);
    }
    return UMemberSelect.create(
        template(tree.getExpression()),
        tree.getIdentifier().toString(),
        template(tree.sym.type));
  }

  private UStaticIdent staticMember(Symbol symbol) {
    return UStaticIdent.create(
        (ClassSymbol) symbol.getEnclosingElement(),
        symbol.getSimpleName().toString(),
        template(symbol.asType()));
  }

  private static final UStaticIdent ANY_OF;
  private static final UStaticIdent IS_INSTANCE;
  private static final UStaticIdent CLAZZ;
  private static final UStaticIdent NEW_ARRAY;
  private static final UStaticIdent ENUM_VALUE_OF;

  static {
    UTypeVar tVar = UTypeVar.create("T");
    ANY_OF = UStaticIdent.create(
        Refaster.class.getCanonicalName(), "anyOf",
        UForAll.create(ImmutableList.of(tVar), UMethodType.create(tVar, UArrayType.create(tVar))));
    IS_INSTANCE = UStaticIdent.create(
        Refaster.class.getCanonicalName(), "isInstance",
        UForAll.create(ImmutableList.of(tVar), 
            UMethodType.create(UPrimitiveType.BOOLEAN,
                UClassType.create(Object.class.getCanonicalName()))));
    CLAZZ = UStaticIdent.create(
        Refaster.class.getCanonicalName(), "clazz",
        UForAll.create(ImmutableList.of(tVar),
            UMethodType.create(UClassType.create(Class.class.getCanonicalName(), tVar))));
    NEW_ARRAY = UStaticIdent.create(
        Refaster.class.getCanonicalName(), "newArray",
        UForAll.create(ImmutableList.of(tVar),
            UMethodType.create(UArrayType.create(tVar), UPrimitiveType.INT)));
    UTypeVar eVar = UTypeVar.create("E",
        UClassType.create(Enum.class.getCanonicalName(), UTypeVar.create("E")));
    ENUM_VALUE_OF = UStaticIdent.create(
        Refaster.class.getCanonicalName(), "enumValueOf",
        UForAll.create(ImmutableList.of(eVar),
            UMethodType.create(eVar, UClassType.create(String.class.getCanonicalName()))));
  }

  private UExpression template(JCMethodInvocation tree) {
    if (ANY_OF.unify(tree.getMethodSelect(), new Unifier(context)) != null) {
      return UAnyOf.create(templateExpressions(tree.getArguments()));
    } else if (IS_INSTANCE.unify(tree.getMethodSelect(), new Unifier(context)) != null) {
      return UInstanceOf.create(
          template(Iterables.getOnlyElement(tree.getArguments())),
          template(getSingleExplicitTypeArgument(tree)));
    } else if (CLAZZ.unify(tree.getMethodSelect(), new Unifier(context)) != null) {
      JCExpression typeArg = getSingleExplicitTypeArgument(tree);
      return UMemberSelect.create(template(typeArg), "class",
          UClassType.create("java.lang.Class", template(typeArg.type)));
    } else if (NEW_ARRAY.unify(tree.getMethodSelect(), new Unifier(context)) != null) {
      JCExpression typeArg = getSingleExplicitTypeArgument(tree);
      JCExpression lengthArg = Iterables.getOnlyElement(tree.getArguments());
      return UNewArray.create(template(typeArg), ImmutableList.of(template(lengthArg)), null);
    } else if (ENUM_VALUE_OF.unify(tree.getMethodSelect(), new Unifier(context)) != null) {
      JCExpression typeArg = getSingleExplicitTypeArgument(tree);
      JCExpression strArg = Iterables.getOnlyElement(tree.getArguments());
      return UMethodInvocation.create(
          UMemberSelect.create(template(typeArg), "valueOf", UMethodType.create(
              template(typeArg.type), UClassType.create("java.lang.String"))),
          template(strArg));
    } else {
      return UMethodInvocation.create(
          template(tree.getMethodSelect()), templateExpressions(tree.getArguments()));
    }
  }

  private static JCExpression getSingleExplicitTypeArgument(JCMethodInvocation tree) {
    if (tree.getTypeArguments().isEmpty()) {
      throw new IllegalArgumentException("Methods in the Refaster class must be invoked with "
          + "an explicit type parameter; for example, 'Refaster.<T>isInstance(o)'.");
    }
    return Iterables.getOnlyElement(tree.getTypeArguments());
  }

  private UBinary template(JCBinary tree) {
    return UBinary.create(
        tree.getKind(), template(tree.getLeftOperand()), template(tree.getRightOperand()));
  }

  private UAssignOp template(JCAssignOp tree) {
    return UAssignOp.create(
        template(tree.getVariable()), tree.getKind(), template(tree.getExpression()));
  }

  private UUnary template(JCUnary tree) {
    return UUnary.create(tree.getKind(), template(tree.getExpression()));
  }

  private UExpression template(JCConditional tree) {
    UConditional result = UConditional.create(
        template(tree.getCondition()),
        template(tree.getTrueExpression()),
        template(tree.getFalseExpression()));
    if (context.get(AlsoReverseTernary.class) != null) {
      return UAnyOf.create(result, result.reverse());
    } else {
      return result;
    }
  }

  private UNewArray template(JCNewArray tree) {
    return UNewArray.create(
        template(tree.getType()),
        templateExpressions(tree.getDimensions()),
        templateExpressions(tree.getInitializers()));
  }

  private UNewClass template(JCNewClass tree) {
    return UNewClass.create(
        tree.getEnclosingExpression() == null ? null : template(tree.getEnclosingExpression()),
        templateExpressions(tree.getTypeArguments()),
        template(tree.getIdentifier()),
        templateExpressions(tree.getArguments()),
        (tree.getClassBody() == null) ? null : template(tree.getClassBody()));
  }
  
  private UClassDecl template(JCClassDecl tree) {
    ImmutableList.Builder<UMethodDecl> decls = ImmutableList.builder();
    for (JCMethodDecl decl : Iterables.filter(tree.getMembers(), JCMethodDecl.class)) {
      if (decl.getReturnType() != null) {
        decls.add(template(decl));
      }
    }
    return UClassDecl.create(decls.build());
  }

  private UArrayTypeTree template(JCArrayTypeTree tree) {
    return UArrayTypeTree.create(template(tree.elemtype));
  }

  private UTypeApply template(JCTypeApply tree) {
    return UTypeApply.create(template(tree.clazz), templateExpressions(tree.arguments));
  }

  private UTypeCast template(JCTypeCast tree) {
    return UTypeCast.create(template(tree.getType()), template(tree.getExpression()));
  }

  private UExpression template(JCIdent tree) {
    if (tree.sym instanceof ClassSymbol) {
      return UClassIdent.create((ClassSymbol) tree.sym);
    } else if (tree.sym.isStatic()) {
      return staticMember(tree.sym);
    } else if (freeVariables.containsKey(tree.getName().toString())) {
      VarSymbol symbol = freeVariables.get(tree.getName().toString());
      checkState(symbol == tree.sym);
      UExpression ident = UFreeIdent.create(tree.getName().toString());
      Matches matches = ASTHelpers.getAnnotation(symbol, Matches.class);
      if (matches != null) {
        ident = UMatches.create(getValue(matches), true, ident);
      }
      NotMatches notMatches = ASTHelpers.getAnnotation(symbol, NotMatches.class);
      if (notMatches != null) {
        ident = UMatches.create(getValue(notMatches), false, ident);
      }
      OfKind hasKind = ASTHelpers.getAnnotation(symbol, OfKind.class);
      if (hasKind != null) {
        EnumSet<Kind> allowed = EnumSet.copyOf(Arrays.asList(hasKind.value()));
        ident = UOfKind.create(ident, ImmutableSet.copyOf(allowed));
      }
      // @Repeated annotations need to be checked last.
      Repeated repeated = ASTHelpers.getAnnotation(symbol, Repeated.class);
      if (repeated != null) {
        ident = URepeated.create(tree.getName().toString(), ident);
      }
      return ident;
    }
    switch (tree.sym.getKind()) {
      case TYPE_PARAMETER:
        return UTypeVarIdent.create(tree.getName().toString());
      default:
        return ULocalVarIdent.create(tree.getName().toString());
    }
  }

  /**
   * Returns the {@link Class} instance for the {@link Matcher} associated with the provided
   * {@link Matches} annotation.  This roundabout solution is recommended and explained by
   * {@link Element#getAnnotation(Class)}.
   */
  private Class<? extends Matcher<? super ExpressionTree>> getValue(Matches matches) {
    String name;
    try {
      matches.value();
      throw new RuntimeException("unreachable");
    } catch (MirroredTypeException e) {
      DeclaredType type = (DeclaredType) e.getTypeMirror();
      name = ((TypeElement) type.asElement()).getQualifiedName().toString();
    }
    try {
      return asSubclass(Class.forName(name), new TypeToken<Matcher<? super ExpressionTree>>() {});
    } catch (ClassNotFoundException|ClassCastException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the {@link Class} instance for the {@link Matcher} associated with the provided
   * {@link NotMatches} annotation.  This roundabout solution is recommended and explained by
   * {@link Element#getAnnotation(Class)}.
   */
  private Class<? extends Matcher<? super ExpressionTree>> getValue(NotMatches matches) {
    String name;
    try {
      matches.value();
      throw new RuntimeException("unreachable");
    } catch (MirroredTypeException e) {
      DeclaredType type = (DeclaredType) e.getTypeMirror();
      name = ((TypeElement) type.asElement()).getQualifiedName().toString();
    }
    try {
      return asSubclass(Class.forName(name), new TypeToken<Matcher<? super ExpressionTree>>() {});
    } catch (ClassNotFoundException|ClassCastException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Similar to {@link Class#asSubclass(Class)}, but it accepts a {@link TypeToken} so it handles
   * generics better.
   */
  @SuppressWarnings("unchecked")
  private <T> Class<? extends T> asSubclass(Class<?> klass, TypeToken<T> token)
      throws ClassCastException{
    if (!token.isAssignableFrom(klass)) {
      throw new ClassCastException(klass + " is not assignable to " + token);
    }
    return (Class<? extends T>) klass;
  }

  public UStatement template(JCStatement tree) {
    if (tree instanceof JCExpressionStatement) {
      return template((JCExpressionStatement) tree);
    } else if (tree instanceof JCForLoop) {
      return template((JCForLoop) tree);
    } else if (tree instanceof JCReturn) {
      return template((JCReturn) tree);
    } else if (tree instanceof JCVariableDecl) {
      return template((JCVariableDecl) tree);
    } else if (tree instanceof JCDoWhileLoop) {
      return template((JCDoWhileLoop) tree);
    } else if (tree instanceof JCThrow) {
      return template((JCThrow) tree);
    } else if (tree instanceof JCBlock) {
      return template((JCBlock) tree);
    } else if (tree instanceof JCIf) {
      return template((JCIf) tree);
    } else if (tree instanceof JCWhileLoop) {
      return template((JCWhileLoop) tree);
    } else if (tree instanceof JCSynchronized) {
      return template((JCSynchronized) tree);
    } else if (tree instanceof JCEnhancedForLoop) {
      return template((JCEnhancedForLoop) tree);
    } else if (tree instanceof JCSkip) {
      return template((JCSkip) tree);
    } else {
      throw new IllegalArgumentException(
          "Refaster does not currently support syntax " + tree.getClass());
    }
  }

  @Nullable
  private List<UStatement> templateStatements(@Nullable List<? extends JCStatement> statements) {
    if (statements == null) {
      return null;
    }
    ImmutableList.Builder<UStatement> builder = ImmutableList.builder();
    for (JCStatement statement : statements) {
      builder.add(template(statement));
    }
    return builder.build();
  }

  private UExpressionStatement template(JCExpressionStatement tree) {
    return UExpressionStatement.create(template(tree.getExpression()));
  }

  private UReturn template(JCReturn tree) {
    return UReturn.create((tree.getExpression() == null) ? null : template(tree.getExpression()));
  }

  private UWhileLoop template(JCWhileLoop tree) {
    return UWhileLoop.create(template(tree.getCondition()), template(tree.getStatement()));
  }

  private UVariableDecl template(JCVariableDecl tree) {
    return UVariableDecl.create(
        tree.getName().toString(),
        template(tree.vartype),
        (tree.getInitializer() == null) ? null : template(tree.getInitializer()));
  }

  private USkip template(JCSkip tree) {
    return USkip.INSTANCE;
  }

  private UForLoop template(JCForLoop tree) {
    return UForLoop.create(
        templateStatements(tree.getInitializer()),
        (tree.getCondition() == null) ? null : template(tree.getCondition()),
        Iterables.filter(templateStatements(tree.getUpdate()), UExpressionStatement.class),
        template(tree.getStatement()));
  }

  private UBlock template(JCBlock tree) {
    return UBlock.create(templateStatements(tree.getStatements()));
  }

  private UThrow template(JCThrow tree) {
    return UThrow.create(template(tree.getExpression()));
  }

  private UDoWhileLoop template(JCDoWhileLoop tree) {
    return UDoWhileLoop.create(template(tree.getStatement()), template(tree.getCondition()));
  }

  private UEnhancedForLoop template(JCEnhancedForLoop tree) {
    return UEnhancedForLoop.create(
        template(tree.getVariable()),
        template(tree.getExpression()),
        template(tree.getStatement()));
  }

  private USynchronized template(JCSynchronized tree) {
    return USynchronized.create(template(tree.getExpression()), template(tree.getBlock()));
  }

  private UIf template(JCIf tree) {
    return UIf.create(
        template(tree.getCondition()),
        template(tree.getThenStatement()),
        (tree.getElseStatement() == null) ? null : template(tree.getElseStatement()));
  }

  public UType template(Type type) {
    if (type instanceof ClassType) {
      return template((ClassType) type);
    } else if (type instanceof ArrayType) {
      return template((ArrayType) type);
    } else if (type instanceof MethodType) {
      return template((MethodType) type);
    } else if (type instanceof WildcardType) {
      return template((WildcardType) type);
    } else if (type instanceof TypeVar) {
      return template((TypeVar) type);
    } else if (type instanceof ForAll) {
      return template((ForAll) type);
    } else if (UPrimitiveType.isDeFactoPrimitive(type.getKind())) {
      return UPrimitiveType.create(type.getKind());
    } else {
      throw new IllegalArgumentException(
          "Refaster does not currently support syntax " + type.getKind());
    }
  }

  private List<UType> templateTypes(List<? extends Type> types) {
    ImmutableList.Builder<UType> builder = ImmutableList.builder();
    for (Type ty : types) {
      builder.add(template(ty));
    }
    return builder.build();
  }

  private UArrayType template(ArrayType type) {
    return UArrayType.create(template(type.getComponentType()));
  }

  private UMethodType template(MethodType type) {
    return UMethodType.create(
        template(type.getReturnType()), templateTypes(type.getParameterTypes()));
  }

  private UClassType template(ClassType type) {
    return UClassType.create(
        type.tsym.getQualifiedName().toString(), templateTypes(type.getTypeArguments()));
  }

  private UWildcardType template(WildcardType type) {
    return UWildcardType.create(type.kind, template(type.type));
  }

  private UTypeVar template(TypeVar type) {
    /*
     * In order to handle recursively bounded type variables without a stack overflow, we first
     * cache a type var with no bounds, then we template the bounds.
     */
    TypeSymbol tsym = type.asElement();
    if (typeVariables.containsKey(tsym)) {
      return typeVariables.get(tsym);
    }
    UTypeVar var = UTypeVar.create(tsym.getSimpleName().toString());
    typeVariables.put(tsym, var); // so the type variable can be used recursively in the bounds
    var.setLowerBound(template(type.getLowerBound()));
    var.setUpperBound(template(type.getUpperBound()));
    return var;
  }

  private UForAll template(ForAll type) {
    List<UTypeVar> vars = FluentIterable.from(templateTypes(type.getTypeVariables()))
        .filter(UTypeVar.class)
        .toList();
    return UForAll.create(vars, template(type.qtype));
  }

  @SuppressWarnings("unchecked")
  public static ImmutableClassToInstanceMap<Annotation> annotationMap(Symbol symbol) {
    ImmutableClassToInstanceMap.Builder<Annotation> builder = ImmutableClassToInstanceMap.builder();
    for (Compound compound : symbol.getAnnotationMirrors()) {
      Name qualifiedAnnotationType =
          ((TypeElement) compound.getAnnotationType().asElement()).getQualifiedName();
      try {
        Class<? extends Annotation> annotationClazz = 
            Class.forName(qualifiedAnnotationType.toString()).asSubclass(Annotation.class);
        builder.put((Class) annotationClazz,
            AnnotationProxyMaker.generateAnnotation(compound, annotationClazz));
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException("Unrecognized annotation type", e);
      }
    }
    return builder.build();
  }
}
