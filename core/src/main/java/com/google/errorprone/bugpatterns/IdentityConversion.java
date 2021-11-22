/*
 * Copyright 2022 The Error Prone Authors.
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

package com.google.errorprone.bugpatterns;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.BugPattern.StandardTags;
import static com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.staticMethod;
import static com.google.errorprone.util.ASTHelpers.TargetType;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.targetType;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Type;
import java.util.List;

@BugPattern(
    name = "IdentityConversion",
    summary = "Unwrap unnecessary identity conversions",
    severity = WARNING,
    tags = StandardTags.SIMPLIFICATION)
public final class IdentityConversion extends BugChecker implements MethodInvocationTreeMatcher {

  private static final Matcher<ExpressionTree> IS_CONVERSION_METHOD =
      anyOf(
          staticMethod()
              .onClassAny(
                  "com.google.common.collect.ImmutableBiMap",
                  "com.google.common.collect.ImmutableList",
                  "com.google.common.collect.ImmutableListMultimap",
                  "com.google.common.collect.ImmutableMap",
                  "com.google.common.collect.ImmutableMultimap",
                  "com.google.common.collect.ImmutableMultiset",
                  "com.google.common.collect.ImmutableRangeSet",
                  "com.google.common.collect.ImmutableSet",
                  "com.google.common.collect.ImmutableSetMultimap",
                  "com.google.common.collect.ImmutableSortedMap",
                  "com.google.common.collect.ImmutableSortedMultiset",
                  "com.google.common.collect.ImmutableSortedSet",
                  "com.google.common.collect.ImmutableTable")
              .named("copyOf"),
          staticMethod()
              .onClassAny(
                  "java.lang.Byte",
                  "java.lang.Character",
                  "java.lang.Double",
                  "java.lang.Float",
                  "java.lang.Integer",
                  "java.lang.String")
              .named("valueOf"),
          staticMethod().onClass("reactor.adapter.rxjava.RxJava2Adapter"),
          staticMethod()
              .onClass("reactor.core.publisher.Flux")
              .namedAnyOf("concat", "firstWithSignal", "from", "merge"),
          staticMethod().onClass("reactor.core.publisher.Mono").namedAnyOf("from", "fromDirect"));

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    if (!IS_CONVERSION_METHOD.matches(tree, state) && tree.getArguments().size() != 1) {
      return Description.NO_MATCH;
    }

    List<? extends ExpressionTree> arguments = tree.getArguments();
    ExpressionTree sourceTree = arguments.get(0);
    Type sourceType = getType(sourceTree);
    TargetType targetType = targetType(state);
    if (sourceType == null
        || targetType == null
        || !state.getTypes().isSubtype(sourceType, targetType.type())) {
      return Description.NO_MATCH;
    }

    return describeMatch(tree, SuggestedFix.replace(tree, state.getSourceForNode(sourceTree)));
  }
}
