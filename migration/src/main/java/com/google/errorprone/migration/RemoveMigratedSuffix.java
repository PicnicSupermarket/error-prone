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

package com.google.errorprone.migration;

import static com.google.errorprone.bugpatterns.BugChecker.MemberReferenceTreeMatcher;
import static com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import static com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;

import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;

// XXX: Not turned on for now, can use Find & Replace of IDE.
// @AutoService(BugChecker.class)
// @BugPattern(
//    name = "RemoveMigratedSuffix",
//    summary = "Without further validation, remove all `_migrated` suffixes from method names.",
//    severity = ERROR)
public class RemoveMigratedSuffix extends BugChecker
    implements MethodTreeMatcher, MethodInvocationTreeMatcher, MemberReferenceTreeMatcher {
  @Override
  public Description matchMethod(MethodTree tree, VisitorState state) {
    String methodName = tree.getName().toString();
    if (methodName.endsWith("_migrated")) {
      return describeMatch(
          tree, SuggestedFixes.renameMethod(tree, methodName.replace("_migrated", ""), state));
    }

    return Description.NO_MATCH;
  }

  @Override
  public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
    String methodName = tree.getName().toString();
    if (methodName.endsWith("_migrated")) {
      return describeMatch(
          tree, SuggestedFix.replace(tree, state.getSourceForNode(tree).replace("_migrated", "")));
    }
    return Description.NO_MATCH;
  }

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    // XXX: Implement this.
    return Description.NO_MATCH;
  }
}
