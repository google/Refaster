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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.testing.compile.JavaFileObjects;

import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

import javax.tools.JavaFileObject;

/**
 * Tests for Refaster expression templates.
 * 
 * @author lowasser@google.com (Louis Wasserman)
 */
@RunWith(JUnit4.class)
public class ExpressionTemplateIntegrationTest extends CompilerBasedTest {
  private CodeTransformer extractRefasterRule(JavaFileObject object) {
    compile(object);
    JCCompilationUnit compilationUnit = Iterables.getOnlyElement(compilationUnits);
    JCClassDecl classDecl = FluentIterable.from(compilationUnit.getTypeDecls())
        .filter(JCClassDecl.class).getOnlyElement();
    return Iterables.getOnlyElement(RefasterRuleBuilderScanner.extractRules(classDecl, context));
  }
  
  private void expectTransforms(CodeTransformer transformer, JavaFileObject input,
      JavaFileObject expectedOutput) throws IOException {
    JavaFileObject transformedInput = 
        CodeTransformerTestHelper.create(transformer).transform(input);
    
    // TODO(lowasser): modify compile-testing to enable direct tree comparison
    assert_().about(javaSource()).that(transformedInput).compilesWithoutError();
    String expectedSource = expectedOutput.getCharContent(false).toString();
    String actualSource = transformedInput.getCharContent(false).toString();
    assertThat(actualSource).isEqualTo(expectedSource);
  }
  
  private static final String TEMPLATE_DIR = "com/google/errorprone/refaster/testdata/template";
  private static final String INPUT_DIR = "com/google/errorprone/refaster/testdata/input";
  private static final String OUTPUT_DIR = "com/google/errorprone/refaster/testdata/output";
  
  @Test
  public void binary() throws IOException {
    CodeTransformer transformer = 
        extractRefasterRule(JavaFileObjects.forResource(TEMPLATE_DIR + "/BinaryTemplate.java"));
    
    JavaFileObject input = JavaFileObjects.forResource(INPUT_DIR + "/BinaryTemplateExample.java");
    JavaFileObject output = 
        JavaFileObjects.forResource(OUTPUT_DIR + "/BinaryTemplateExample.java");
    expectTransforms(transformer, input, output);
  }
  
  @Test
  public void parenthesesOptional() throws IOException {
    CodeTransformer transformer = extractRefasterRule(
        JavaFileObjects.forResource(TEMPLATE_DIR + "/ParenthesesOptionalTemplate.java"));
    
    JavaFileObject input = 
        JavaFileObjects.forResource(INPUT_DIR + "/ParenthesesOptionalTemplateExample.java");
    JavaFileObject output = 
        JavaFileObjects.forResource(OUTPUT_DIR + "/ParenthesesOptionalTemplateExample.java");
    expectTransforms(transformer, input, output);
  }
  
  @Test
  public void multipleReferencesToIdentifier() throws IOException {
    CodeTransformer transformer = extractRefasterRule(JavaFileObjects.forResource(
        TEMPLATE_DIR + "/MultipleReferencesToIdentifierTemplate.java"));
    
    JavaFileObject input = JavaFileObjects.forResource(
        INPUT_DIR + "/MultipleReferencesToIdentifierTemplateExample.java");
    JavaFileObject output = JavaFileObjects.forResource(
        OUTPUT_DIR + "/MultipleReferencesToIdentifierTemplateExample.java");
    expectTransforms(transformer, input, output);
  }
  
  @Test
  public void methodInvocation() throws IOException {
    CodeTransformer transformer = extractRefasterRule(
        JavaFileObjects.forResource(TEMPLATE_DIR + "/MethodInvocationTemplate.java"));
    
    JavaFileObject input = 
        JavaFileObjects.forResource(INPUT_DIR + "/MethodInvocationTemplateExample.java");
    JavaFileObject output = 
        JavaFileObjects.forResource(OUTPUT_DIR + "/MethodInvocationTemplateExample.java");
    expectTransforms(transformer, input, output);
  }
}
