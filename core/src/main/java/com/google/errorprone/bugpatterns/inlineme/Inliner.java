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

package com.google.errorprone.bugpatterns.inlineme;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.bugpatterns.BugChecker.MemberReferenceTreeMatcher;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.method.MethodMatchers.instanceMethod;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;
import static com.google.errorprone.util.ASTHelpers.enclosingPackage;
import static com.google.errorprone.util.ASTHelpers.findEnclosingMethod;
import static com.google.errorprone.util.ASTHelpers.getReceiver;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;
import static com.google.errorprone.util.ASTHelpers.hasDirectAnnotationWithSimpleName;
import static com.google.errorprone.util.ASTHelpers.stringContainsComments;
import static com.google.errorprone.util.MoreAnnotations.getValue;
import static com.google.errorprone.util.SideEffectAnalysis.hasSideEffect;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.util.MoreAnnotations;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Checker that performs the inlining at call-sites (where the invoked APIs are annotated as
 * {@code @InlineMe}).
 */
@BugPattern(
    name = "InlineMeInliner",
    summary = "Callers of this API should be inlined.",
    severity = WARNING,
    tags = Inliner.FINDING_TAG)
public final class Inliner extends BugChecker
    implements MethodInvocationTreeMatcher, NewClassTreeMatcher, MemberReferenceTreeMatcher {

  public static final String FINDING_TAG = "JavaInlineMe";

  static final String PREFIX_FLAG = "InlineMe:Prefix";
  static final String SKIP_COMMENTS_FLAG = "InlineMe:SkipInliningsWithComments";
  static final String ALLOW_BREAKING_CHANGES_FLAG = "InlineMe:AllowBreakingChanges";

  private static final Splitter PACKAGE_SPLITTER = Splitter.on('.');

  private static final String CHECK_FIX_COMPILES = "InlineMe:CheckFixCompiles";

  private static final String INLINE_ME = "InlineMe";
  private static final String VALIDATION_DISABLED = "InlineMeValidationDisabled";

  private static final com.google.errorprone.matchers.Matcher<ExpressionTree> MOCKITO_MATCHER =
      anyOf(
          staticMethod().onClass("org.mockito.Mockito").named("when"),
          instanceMethod().onDescendantOf("org.mockito.stubbing.Stubber").named("when"),
          staticMethod().onClass("org.mockito.Mockito").named("verify"),
          staticMethod().onClass("org.testng.Assert").named("assertSame"));

  private final ImmutableSet<String> apiPrefixes;
  private final boolean skipCallsitesWithComments;
  private final boolean checkFixCompiles;
  private final boolean allowBreakingChanges;

  public Inliner(ErrorProneFlags flags) {
    this.apiPrefixes =
        ImmutableSet.copyOf(flags.getSet(PREFIX_FLAG).orElse(ImmutableSet.<String>of()));
    this.skipCallsitesWithComments = flags.getBoolean(SKIP_COMMENTS_FLAG).orElse(true);
    this.checkFixCompiles = flags.getBoolean(CHECK_FIX_COMPILES).orElse(false);
    this.allowBreakingChanges = flags.getBoolean(ALLOW_BREAKING_CHANGES_FLAG).orElse(false);
  }

  @Override
  public Description matchNewClass(NewClassTree tree, VisitorState state) {
    MethodSymbol symbol = getSymbol(tree);
    if (!hasDirectAnnotationWithSimpleName(symbol, INLINE_ME)) {
      return Description.NO_MATCH;
    }
    ImmutableList<String> callingVars =
        tree.getArguments().stream().map(state::getSourceForNode).collect(toImmutableList());

    String receiverString = "new " + state.getSourceForNode(tree.getIdentifier());

    return match(tree, symbol, callingVars, receiverString, null, state);
  }

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    MethodSymbol symbol = getSymbol(tree);
    MethodTree enclosingMethod = findEnclosingMethod(state);
    if (!hasDirectAnnotationWithSimpleName(symbol, INLINE_ME)
        || isMockMethodThatCannotBeInlined(tree, state)
        || isEnclosingMethodAnAlreadyMigratedInterface(tree, enclosingMethod, state)) {
      return Description.NO_MATCH;
    }
    //    if (!hasAnnotation(symbol, INLINE_ME, state)
    //        || isMockMethodThatCannotBeInlined(tree, state)
    //        || isEnclosingMethodAnAlreadyMigratedInterface(tree, enclosingMethod, state)) {
    //      return Description.NO_MATCH;
    //    }

    ImmutableList<String> callingVars =
        tree.getArguments().stream().map(state::getSourceForNode).collect(toImmutableList());

    String receiverString = "";

    ExpressionTree receiver = getReceiver(tree);
    if (receiver != null) {
      receiverString = state.getSourceForNode(receiver);
    }

    ExpressionTree methodSelectTree = tree.getMethodSelect();
    if (methodSelectTree != null) {
      String methodSelect = state.getSourceForNode(methodSelectTree);
      if ("super".equals(methodSelect)) {
        receiverString = methodSelect;
      }
      // TODO(kak): Can we omit the `this` case? The getReceiver() call above handles `this`
      if ("this".equals(methodSelect)) {
        receiverString = methodSelect;
      }
    }

    return match(tree, symbol, callingVars, receiverString, receiver, state);
  }

  public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
    MethodSymbol symbol = getSymbol(tree);
    MethodTree enclosingMethod = findEnclosingMethod(state);
    if (!hasDirectAnnotationWithSimpleName(symbol, INLINE_ME)
        || isEnclosingMethodAnAlreadyMigratedInterface(tree, enclosingMethod, state)) {
      return Description.NO_MATCH;
    }

    String receiverString =
        getReceiver(tree).toString().startsWith("this") ? "" : getReceiver(tree).toString();

    ImmutableList<String> callingVars;
    if (symbol.params().size() < 2) {
      callingVars = ImmutableList.of("ident");
    } else {
      callingVars = ImmutableList.of("ident1", "ident2");
    }
    Description match = match(tree, symbol, callingVars, receiverString, getReceiver(tree), state);

    String lambdaPrefix = getLambdaPrefix(symbol);

    SuggestedFix fix =
        Stream.of(
                SuggestedFix.prefixWith(tree, lambdaPrefix + " -> "),
                (SuggestedFix) match.fixes.get(0))
            .reduce(
                SuggestedFix.builder(), SuggestedFix.Builder::merge, SuggestedFix.Builder::merge)
            .build();

    return describeMatch(tree, fix);
  }

  // XXX: Improve this method, this is now quick solution and ugly.
  private String getLambdaPrefix(MethodSymbol symbol) {
    String lambdaPrefix = symbol.getParameters().size() == 0 ? "()" : "ident";
    if (symbol.params().size() == 1) {
      String type = symbol.params().get(0).type.toString();
      lambdaPrefix = "(" + type + " " + lambdaPrefix + ")";
    } else if (symbol.params().size() == 2) {
      String type = symbol.params().get(0).type.toString();
      String type2 = symbol.params().get(1).type.toString();
      lambdaPrefix = "(" + type + " ident1, " + type2 + " ident2)";
    }
    return lambdaPrefix;
  }

  /**
   * Checks whether the enclosing method is a _migrated method *and* in an interface. There is an
   * extra check, to ensure that the check is looking for the same method name.
   *
   * <p>Assume you have method A, A_migrated, B, and B_migrated. Without the check there is a
   * special case which would not be migrated. When A_migrated call B in the default body of an
   * interface, the call to B was not migrated to B_migrated.
   */
  private boolean isEnclosingMethodAnAlreadyMigratedInterface(
      ExpressionTree currentMethodInvocation, MethodTree enclosingMethod, VisitorState state) {
    ExpressionTree methodSelect =
        currentMethodInvocation instanceof MethodInvocationTree
            ? ((MethodInvocationTree) currentMethodInvocation).getMethodSelect()
            : ((MemberReferenceTree) currentMethodInvocation).getQualifierExpression();

    String migratedNameOfCurrentMethodInvocation = methodSelect.toString() + "_migrated";
    return enclosingMethod != null
        && (methodSelect.equals(enclosingMethod.getName())
            || migratedNameOfCurrentMethodInvocation.equals(enclosingMethod.getName().toString()))
        && enclosingMethod.getName().toString().contains("_migrated")
        && ASTHelpers.getSymbol(getEnclosingClass(state.getPath())).isInterface();
  }

  //  XXX: This is also in FindIdentifiers, where should this method be?
  @Nullable
  private static ClassTree getEnclosingClass(TreePath treePath) {
    while (treePath != null) {
      TreePath parent = treePath.getParentPath();
      if (parent == null) {
        return null;
      }
      Tree leaf = parent.getLeaf();
      if (leaf instanceof ClassTree
          && ((ClassTree) leaf).getMembers().contains(treePath.getLeaf())) {
        return (ClassTree) leaf;
      }
      treePath = parent;
    }
    return null;
  }

  private static boolean isMockMethodThatCannotBeInlined(
      MethodInvocationTree tree, VisitorState state) {
    Tree parent = state.getPath().getParentPath().getLeaf();
    ExpressionTree receiverExpr = getReceiver(tree);
    return (parent instanceof MethodInvocationTree
            && MOCKITO_MATCHER.matches(((MethodInvocationTree) parent).getMethodSelect(), state))
        || (receiverExpr instanceof MethodInvocationTree
            && MOCKITO_MATCHER.matches(
                ((MethodInvocationTree) receiverExpr).getMethodSelect(), state));
  }

  private Description match(
      ExpressionTree tree,
      MethodSymbol symbol,
      ImmutableList<String> callingVars,
      String receiverString,
      ExpressionTree receiver,
      VisitorState state) {
    Optional<InlineMeData> inlineMe = InlineMeData.createFromSymbol(symbol);
    if (inlineMe.isEmpty()) {
      return Description.NO_MATCH;
    }
    checkState(hasDirectAnnotationWithSimpleName(symbol, INLINE_ME));

    Api api = Api.create(symbol, state);
    if (!matchesApiPrefixes(api)) {
      return Description.NO_MATCH;
    }

    if (skipCallsitesWithComments
        && stringContainsComments(state.getSourceForNode(tree), state.context)) {
      return Description.NO_MATCH;
    }

    SuggestedFix.Builder builder = SuggestedFix.builder();

    Map<String, String> typeNames = new HashMap<>();
    for (String newImport : inlineMe.get().imports()) {
      String typeName = Iterables.getLast(PACKAGE_SPLITTER.split(newImport));
      String qualifiedTypeName = SuggestedFixes.qualifyType(state, builder, newImport);
      typeNames.put(typeName, qualifiedTypeName);
    }
    for (String newStaticImport : inlineMe.get().staticImports()) {
      builder.addStaticImport(newStaticImport);
    }

    ImmutableList<String> varNames =
        symbol.getParameters().stream()
            .map(varSymbol -> varSymbol.getSimpleName().toString())
            .collect(toImmutableList());

    boolean varargsWithEmptyArguments = false;
    if (symbol.isVarArgs()) {
      // If we're calling a varargs method, its inlining *should* have the varargs parameter in a
      // reasonable position. If there are are 0 arguments, we'll need to do more surgery
      if (callingVars.size() == varNames.size() - 1) {
        varargsWithEmptyArguments = true;
      } else {
        ImmutableList<String> nonvarargs = callingVars.subList(0, varNames.size() - 1);
        String varargsJoined =
            Joiner.on(", ").join(callingVars.subList(varNames.size() - 1, callingVars.size()));
        callingVars =
            ImmutableList.<String>builderWithExpectedSize(varNames.size())
                .addAll(nonvarargs)
                .add(varargsJoined)
                .build();
      }
    }

    String replacement = inlineMe.get().replacement();
    int replacementStart = ((DiagnosticPosition) tree).getStartPosition();
    int replacementEnd = state.getEndPosition(tree);

    // Special case replacements starting with "this." so the receiver portion is not included in
    // the replacement. This avoids overlapping replacement regions for fluent chains.
    if (replacement.startsWith("this.") && receiver != null) {
      replacementStart = state.getEndPosition(receiver);
      replacement = replacement.substring("this".length());
    }

    if (Strings.isNullOrEmpty(receiverString)) {
      replacement = replacement.replaceAll("\\bthis\\.\\b", "");
    } else {
      if (replacement.equals("this")) { // e.g.: foo.b() -> foo
        Tree parent = state.getPath().getParentPath().getLeaf();
        // If the receiver is a side-effect-free expression and the whole expression is standalone,
        // the receiver likely can't stand on its own (e.g.: "foo;" is not a valid statement while
        // "foo.noOpMethod();" is).
        if (parent instanceof ExpressionStatementTree && !hasSideEffect(receiver)) {
          return describe(parent, SuggestedFix.delete(parent), api);
        }
      }
      replacement = replacement.replaceAll("\\bthis\\b", receiverString);
    }

    // Qualify imports first, then replace parameter values to avoid clobbering source from the
    // inlined method.
    for (Map.Entry<String, String> typeName : typeNames.entrySet()) {
      // TODO(b/189535612): we'll need to be smarter about our replacements (to avoid clobbering
      // inline parameter comments like /* paramName= */
      replacement =
          replacement.replaceAll(
              "\\b" + Pattern.quote(typeName.getKey()) + "\\b",
              Matcher.quoteReplacement(typeName.getValue()));
    }
    for (int i = 0; i < varNames.size(); i++) {
      // Ex: foo(int a, int... others) -> this.bar(a, others)
      // If caller passes 0 args in the varargs position, we want to remove the preceding comma to
      // make this.bar(a) (as opposed to "this.bar(a, )"
      boolean terminalVarargsReplacement = varargsWithEmptyArguments && i == varNames.size() - 1;
      String capturePrefixForVarargs = terminalVarargsReplacement ? "(?:,\\s*)?" : "";
      // We want to avoid replacing a method invocation with the same name as the method.
      Pattern extractArgAndNextToken =
          Pattern.compile(
              "\\b" + capturePrefixForVarargs + Pattern.quote(varNames.get(i)) + "\\b([^(])");
      String replacementResult =
          Matcher.quoteReplacement(terminalVarargsReplacement ? "" : callingVars.get(i)) + "$1";
      Matcher matcher = extractArgAndNextToken.matcher(replacement);
      replacement = matcher.replaceAll(replacementResult);
    }

    builder.replace(replacementStart, replacementEnd, replacement);

    SuggestedFix fix = builder.build();

    // If there are no imports to add, then there's no new dependencies, so we can verify that it
    // compilesWithFix(); if there are new imports to add, then we can't validate that it compiles.
    if (fix.getImportsToAdd().isEmpty()
        && !allowBreakingChanges
        && !(tree instanceof MemberReferenceTree)) {
      return SuggestedFixes.compilesWithFix(fix, state)
          ? describe(tree, fix, api)
          : Description.NO_MATCH;
    }

    return describe(tree, fix, api);
  }

  private static ImmutableList<String> getStrings(Attribute.Compound attribute, String name) {
    return getValue(attribute, name)
        .map(MoreAnnotations::asStrings)
        .orElse(Stream.empty())
        .collect(toImmutableList());
  }

  private Description describe(Tree tree, SuggestedFix fix, Api api) {
    return buildDescription(tree).setMessage(api.message()).addFix(fix).build();
  }

  @AutoValue
  abstract static class Api {
    private static final Splitter CLASS_NAME_SPLITTER = Splitter.on('.');

    static Api create(MethodSymbol method, VisitorState state) {
      String extraMessage = "";
      if (hasDirectAnnotationWithSimpleName(method, VALIDATION_DISABLED)) {
        Attribute.Compound inlineMeValidationDisabled =
            method.getRawAttributes().stream()
                .filter(a -> a.type.tsym.getSimpleName().contentEquals(VALIDATION_DISABLED))
                .collect(onlyElement());
        String reason = Iterables.getOnlyElement(getStrings(inlineMeValidationDisabled, "value"));
        extraMessage = " NOTE: this is an unvalidated inlining! Reasoning: " + reason;
      }
      return new AutoValue_Inliner_Api(
          method.owner.getQualifiedName().toString(),
          method.getSimpleName().toString(),
          enclosingPackage(method).toString(),
          method.isConstructor(),
          hasAnnotation(method, "java.lang.Deprecated", state),
          extraMessage);
    }

    abstract String className();

    abstract String methodName();

    abstract String packageName();

    abstract boolean isConstructor();

    abstract boolean isDeprecated();

    abstract String extraMessage();

    final String message() {
      return "Migrate (via inlining) from "
          + (isDeprecated() ? "deprecated " : "")
          + shortName()
          + " to its replacement"
          + extraMessage();
    }

    /** Returns {@code FullyQualifiedClassName#methodName}. */
    final String methodId() {
      return String.format("%s#%s", className(), methodName());
    }

    /**
     * Returns a short, human readable description of this API in markdown format (e.g., {@code
     * `ClassName.methodName()`}).
     */
    final String shortName() {
      String humanReadableClassName = className().replaceFirst(packageName() + ".", "");
      return String.format("`%s.%s()`", humanReadableClassName, methodName());
    }

    /** Returns the simple class name (e.g., {@code ClassName}). */
    final String simpleClassName() {
      return Iterables.getLast(CLASS_NAME_SPLITTER.split(className()));
    }
  }

  private boolean matchesApiPrefixes(Api api) {
    if (apiPrefixes.isEmpty()) {
      return true;
    }
    for (String apiPrefix : apiPrefixes) {
      if (api.methodId().startsWith(apiPrefix)) {
        return true;
      }
    }
    return false;
  }
}
