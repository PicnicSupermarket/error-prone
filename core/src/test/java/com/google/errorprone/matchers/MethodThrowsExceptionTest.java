/*
 * Copyright 2021 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.matchers;

import com.google.errorprone.VisitorState;
import com.google.errorprone.scanner.Scanner;
import com.sun.source.doctree.DocTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Type;
import org.hamcrest.core.IsSame;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.google.errorprone.matchers.Matchers.*;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class MethodThrowsExceptionTest extends CompilerBasedAbstractTest {
  final List<ScannerTest> tests = new ArrayList<>();

  @After
  public void tearDown() {
    for (ScannerTest test : tests) {
      test.assertDone();
    }
  }

  @Test
  public void shouldMatchThis() {
    writeFile(
        "A.java",
        "public class A {",
        " A() throws Exception { int foo = 1; throw new NullPointerException(); }",
        " A(int foo) { int x = foo; }",
        "}");
    assertCompiles(
        methodThrowsException(
            /* shouldMatch= */ true,
            throwsException(
                new Matcher<Tree>() {
                  @Override
                  public boolean matches(Tree tree, VisitorState state) {
                    return tree.getKind().equals("java.lang.Exception");
                  }
                })));
  }

  @Test
  public void shouldMatchOne() {
    writeFile(
        "A.java",
        "class A {",
        "@FunctionalInterface",
        "interface MyExceptionThrowingFunctionalInterface<I, O> {",
        "   O fun(I input) throws Exception;",
        "}",
        "",
        "void receiver(MyExceptionThrowingFunctionalInterface fun) {",
        "   receiver(this::fun2);",
        "}",
        "",
        //        "private String fun1(Object o) {",
        //        "   return o.toString();",
        //        "}",
        "",
        "private String fun2(Object o) throws Exception {",
        "   return o.toString();",
        "}",
        "",
        "}");

    assertCompiles(
        methodThrowsException(
            /* shouldMatch= */ true,
            Matchers.throwsException(variableType(isSameType("java.lang.Exception")))
                    //                new Matcher<Tree>() {
//                  @Override
//                  public boolean matches(Tree tree, VisitorState state) {
//                      return isSameType("java.lang.Exception");
////                      return tree.getType().equals("");
////                    return tree.getName().contentEquals("Exception");
//                  }
                ));
  }

  //     @Override
  //                                  public boolean matches(MethodTree tree, VisitorState state) {
  //                                      return tree.getName().contentEquals("Exception");
  //                                  }

  private abstract static class ScannerTest extends Scanner {
    abstract void assertDone();
  }

  private Scanner methodThrowsException(
      final boolean shouldMatch, final Matcher<MemberReferenceTree> toMatch) {
    ScannerTest test =
        new ScannerTest() {
          private boolean matched = false;

          @Override
          public Void visitMemberReference(MemberReferenceTree node, VisitorState visitorState) {
            visitorState = visitorState.withPath(getCurrentPath());
            if (toMatch.matches(node, visitorState)) {
              matched = true;
            }

            return super.visitMemberReference(node, visitorState);
          }

          //            @Override
          //          public Void visitMethod(MethodTree node, VisitorState visitorState) {
          //            visitorState = visitorState.withPath(getCurrentPath());
          //            if (toMatch.matches(node, visitorState)) {
          //              matched = true;
          //            }
          //            return super.visitMethod(node, visitorState);
          //          }

          @Override
          public void assertDone() {
            assertThat(shouldMatch).isEqualTo(matched);
          }
        };
    tests.add(test);
    return test;
  }
}
