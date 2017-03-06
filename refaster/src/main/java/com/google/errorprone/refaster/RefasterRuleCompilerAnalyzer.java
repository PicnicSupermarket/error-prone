/*
 * Copyright 2016 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.errorprone.refaster;

import com.google.errorprone.CodeTransformer;
import com.google.errorprone.CompositeCodeTransformer;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TaskListener that receives compilation of a Refaster rule class and outputs a serialized analyzer
 * to the specified path.
 */
public class RefasterRuleCompilerAnalyzer implements TaskListener {
  private final List<CodeTransformer> rules = Collections.synchronizedList(new ArrayList<>());
  private final Context context;
  private final Path destinationPath;

  RefasterRuleCompilerAnalyzer(Context context, Path destinationPath) {
    this.context = context;
    this.destinationPath = destinationPath;
  }

  @Override
  public void finished(TaskEvent taskEvent) {
    if (taskEvent.getKind() != Kind.ANALYZE) {
      return;
    }
    if (JavaCompiler.instance(context).errorCount() > 0) {
      return;
    }
    ClassTree tree = JavacTrees.instance(context).getTree(taskEvent.getTypeElement());
    if (tree == null) {
      return;
    }
    rules.addAll(RefasterRuleBuilderScanner.extractRules(tree, context));
  }

  public void persist() {
    try (ObjectOutputStream output =
        new ObjectOutputStream(Files.newOutputStream(destinationPath))) {
      output.writeObject(CompositeCodeTransformer.compose(rules));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
