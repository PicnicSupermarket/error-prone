/*
 * Copyright 2017 The Error Prone Authors.
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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.method.MethodMatchers.instanceMethod;
import static com.google.errorprone.util.ASTHelpers.getStartPosition;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.hasImplicitType;
import static com.google.errorprone.util.ASTHelpers.isSubtype;
import static com.google.errorprone.util.Regexes.convertRegexToLiteral;
import static java.lang.String.format;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.Fix;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.util.SourceCodeEscapers;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(summary = "String.split(String) has surprising behavior", severity = WARNING)
public class StringSplitter extends BugChecker implements MethodInvocationTreeMatcher {

  private static final Matcher<ExpressionTree> MATCHER =
      anyOf(
          instanceMethod()
              .onExactClass("java.lang.String")
              .named("split")
              .withParameters("java.lang.String"),
          instanceMethod()
              .onExactClass("java.util.regex.Pattern")
              .named("split")
              .withParameters("java.lang.CharSequence"));

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    if (!MATCHER.matches(tree, state)) {
      return NO_MATCH;
    }
    Optional<Fix> fix = buildFix(tree, state);
    if (!fix.isPresent()) {
      return NO_MATCH;
    }
    // TODO(b/112270644): skip Splitter fix if guava isn't on the classpath
    return describeMatch(tree, fix.get());
  }

  public Optional<Fix> buildFix(MethodInvocationTree tree, VisitorState state) {
    ExpressionTree arg = getOnlyElement(tree.getArguments());
    Tree parent = state.getPath().getParentPath().getLeaf();
    if (parent instanceof EnhancedForLoopTree enhancedForLoopTree
        && enhancedForLoopTree.getExpression().equals(tree)) {
      // fix for `for (... : s.split(...)) {}` -> `for (... : Splitter.on(...).split(s)) {}`
      return Optional.of(
          replaceWithSplitter(
                  SuggestedFix.builder(), tree, arg, state, "split", /* mutableList= */ false)
              .build());
    }
    if (parent instanceof ArrayAccessTree arrayAccessTree) {
      if (!arrayAccessTree.getExpression().equals(tree)) {
        return Optional.empty();
      }
      SuggestedFix.Builder fix =
          SuggestedFix.builder()
              .addImport("com.google.common.collect.Iterables")
              .replace(
                  getStartPosition(arrayAccessTree),
                  getStartPosition(arrayAccessTree),
                  "Iterables.get(")
              .replace(
                  /* startPos= */ state.getEndPosition(arrayAccessTree.getExpression()),
                  /* endPos= */ getStartPosition(arrayAccessTree.getIndex()),
                  format(", "))
              .replace(
                  state.getEndPosition(arrayAccessTree.getIndex()),
                  state.getEndPosition(arrayAccessTree),
                  ")");
      return Optional.of(
          replaceWithSplitter(fix, tree, arg, state, "split", /* mutableList= */ false).build());
    }
    // If the result of split is assigned to a variable, try to fix all uses of the variable in the
    // enclosing method. If we don't know how to fix any of them, bail out.
    if (!(parent instanceof VariableTree varTree)) {
      return Optional.empty();
    }
    if (!varTree.getInitializer().equals(tree)) {
      return Optional.empty();
    }
    VarSymbol sym = ASTHelpers.getSymbol(varTree);
    TreePath enclosing = findEnclosing(state);
    if (enclosing == null) {
      return Optional.empty();
    }
    // find all uses of the variable in the enclosing method
    List<TreePath> uses = new ArrayList<>();
    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitIdentifier(IdentifierTree tree, Void unused) {
        if (Objects.equals(sym, ASTHelpers.getSymbol(tree))) {
          uses.add(getCurrentPath());
        }
        return super.visitIdentifier(tree, null);
      }
    }.scan(enclosing, null);
    SuggestedFix.Builder fix = SuggestedFix.builder();
    // a mutable boolean to track whether we want split or splitToList
    boolean[] needsList = {false};
    boolean[] needsMutableList = {false};
    // try to fix all uses of the variable
    for (TreePath path : uses) {
      class UseFixer extends TreePathScanner<Boolean, Void> {
        @Override
        public Boolean visitEnhancedForLoop(EnhancedForLoopTree tree, Void unused) {
          // The syntax for looping over an array or iterable variable is the same, so there's no
          // fix here.
          return sym.equals(ASTHelpers.getSymbol(tree.getExpression()));
        }

        @Override
        public Boolean visitArrayAccess(ArrayAccessTree tree, Void unused) {
          // replace `pieces[N]` with `pieces.get(N)`
          ExpressionTree expression = tree.getExpression();
          ExpressionTree index = tree.getIndex();
          if (!sym.equals(ASTHelpers.getSymbol(expression))) {
            return false;
          }
          Tree parent = getCurrentPath().getParentPath().getLeaf();
          if (parent instanceof AssignmentTree assignmentTree
              && assignmentTree.getVariable() == tree) {
            fix.replace(
                    /* startPos= */ state.getEndPosition(expression),
                    /* endPos= */ getStartPosition(index),
                    ".set(")
                .replace(
                    /* startPos= */ state.getEndPosition(index),
                    /* endPos= */ getStartPosition(assignmentTree.getExpression()),
                    ", ")
                .postfixWith(assignmentTree, ")");
            needsMutableList[0] = true;
          } else {
            fix.replace(
                    /* startPos= */ state.getEndPosition(expression),
                    /* endPos= */ getStartPosition(index),
                    ".get(")
                .replace(state.getEndPosition(index), state.getEndPosition(tree), ")");
          }
          // we want a list for indexing
          needsList[0] = true;
          return true;
        }

        @Override
        public Boolean visitMemberSelect(MemberSelectTree tree, Void unused) {
          // replace `pieces.length` with `pieces.size`
          if (sym.equals(ASTHelpers.getSymbol(tree.getExpression()))
              && tree.getIdentifier().contentEquals("length")) {
            fix.replace(
                state.getEndPosition(tree.getExpression()), state.getEndPosition(tree), ".size()");
            needsList[0] = true;
            return true;
          }
          return false;
        }
      }
      if (!firstNonNull(new UseFixer().scan(path.getParentPath(), null), false)) {
        return Optional.empty();
      }
    }

    Tree varType = varTree.getType();
    boolean isImplicitlyTyped = hasImplicitType(varTree, state); // Is it a use of `var`?
    if (needsList[0]) {
      if (!isImplicitlyTyped) {
        fix.replace(varType, "List<String>").addImport("java.util.List");
      }
      return Optional.of(
          replaceWithSplitter(fix, tree, arg, state, "splitToList", needsMutableList[0]).build());
    }
    if (!isImplicitlyTyped) {
      fix.replace(varType, "Iterable<String>");
    }
    return Optional.of(
        replaceWithSplitter(fix, tree, arg, state, "split", needsMutableList[0]).build());
  }

  private static String getMethodAndArgument(
      SuggestedFix.Builder fix, Tree origArg, VisitorState state) {
    String argSource = state.getSourceForNode(origArg);
    Tree arg = ASTHelpers.stripParentheses(origArg);
    if (arg.getKind() != Tree.Kind.STRING_LITERAL) {
      // Even if the regex is a constant, it still needs to be treated as a regex, since the
      // value comes from the symbol and/or a concatenation; the values of the subexpressions may be
      // changed subsequently.
      return onPattern(fix, argSource);
    }
    String constValue = ASTHelpers.constValue(arg, String.class);
    if (constValue == null) {
      // Not a constant value, so we can't assume anything about pattern: have to treat it as a
      // regex.
      return onPattern(fix, argSource);
    }
    Optional<String> regexAsLiteral = convertRegexToLiteral(constValue);
    if (!regexAsLiteral.isPresent()) {
      // Can't convert the regex to a literal string: have to treat it as a regex.
      return onPattern(fix, argSource);
    }
    String escaped = SourceCodeEscapers.javaCharEscaper().escape(regexAsLiteral.get());
    if (regexAsLiteral.get().length() == 1) {
      return String.format("on('%s')", escaped);
    }
    return String.format("on(\"%s\")", escaped);
  }

  private static String onPattern(SuggestedFix.Builder fix, String argSource) {
    fix.addImport("java.util.regex.Pattern");
    return String.format("on(Pattern.compile(%s))", argSource);
  }

  private static SuggestedFix.Builder replaceWithSplitter(
      SuggestedFix.Builder fix,
      MethodInvocationTree tree,
      ExpressionTree arg,
      VisitorState state,
      String splitMethod,
      boolean mutableList) {
    ExpressionTree receiver = ASTHelpers.getReceiver(tree);
    if (mutableList) {
      fix.addImport("java.util.ArrayList");
    }
    fix.addImport("com.google.common.base.Splitter");
    Type receiverType = getType(receiver);
    if (isSubtype(receiverType, state.getSymtab().stringType, state)) {
      String methodAndArgument = getMethodAndArgument(fix, arg, state);
      return fix.prefixWith(
              receiver,
              String.format(
                  "%sSplitter.%s.%s(",
                  (mutableList ? "new ArrayList<>(" : ""), methodAndArgument, splitMethod))
          .replace(
              state.getEndPosition(receiver),
              state.getEndPosition(tree),
              (mutableList ? ")" : "") + ")");
    }
    if (isSubtype(receiverType, JAVA_UTIL_REGEX_PATTERN.get(state), state)) {
      return fix.prefixWith(
              receiver, String.format("%sSplitter.on(", (mutableList ? "new ArrayList<>(" : "")))
          .postfixWith(receiver, ")")
          .replace(
              /* startPos= */ state.getEndPosition(receiver),
              /* endPos= */ getStartPosition(arg),
              String.format(".%s(", splitMethod))
          .replace(
              state.getEndPosition(arg),
              state.getEndPosition(tree),
              (mutableList ? ")" : "") + ")");
    }
    throw new AssertionError(receiver);
  }

  private static @Nullable TreePath findEnclosing(VisitorState state) {
    for (TreePath path = state.getPath(); path != null; path = path.getParentPath()) {
      switch (path.getLeaf().getKind()) {
        case METHOD, LAMBDA_EXPRESSION -> {
          return path;
        }
        case CLASS -> {
          return null;
        }
        default -> {}
      }
    }
    return null;
  }

  private static final Supplier<Type> JAVA_UTIL_REGEX_PATTERN =
      VisitorState.memoize(state -> state.getTypeFromString("java.util.regex.Pattern"));
}
