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

import com.google.auto.service.AutoService;
import com.google.common.collect.Iterables;
import com.google.errorprone.InvalidCommandLineOptionException;
import com.google.errorprone.MaskedClassLoader;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.main.Main.Result;
import com.sun.tools.javac.util.Context;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

/**
 * A javac plugin that compiles Refaster rules to a {@code .analyzer} file.
 *
 * @author lowasser@google.com
 */
@AutoService(Plugin.class)
public class RefasterRuleCompiler implements Plugin {
  @Override
  public String getName() {
    return "RefasterRuleCompiler";
  }

  @Override
  public void init(JavacTask javacTask, String... args) {
    String path = extractOutputPath(new ArrayList<>(Arrays.asList(args)));

    javacTask.addTaskListener(
        new RefasterRuleCompilerAnalyzer(
            ((BasicJavacTask) javacTask).getContext(), FileSystems.getDefault().getPath(path)));
  }

  public static void main(String[] args) {
    new RefasterRuleCompiler().run(args, new Context());
  }

  private Result run(String[] argv, Context context) {
    try {
      argv = prepareCompilation(argv, context);
    } catch (InvalidCommandLineOptionException e) {
      System.err.println(e.getMessage());
      System.err.flush();
      return Result.CMDERR;
    }

    try {
      Result compileResult =
          new Main(
                  getName(),
                  new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8)))
              .compile(argv, context);
      System.err.flush();
      return compileResult;
    } catch (InvalidCommandLineOptionException e) {
      System.err.println(e.getMessage());
      System.err.flush();
      return Result.CMDERR;
    }
  }

  private String[] prepareCompilation(String[] argv, Context context)
      throws InvalidCommandLineOptionException {
    context.put(DiagnosticListener.class, new DiagnosticCollector<JavaFileObject>());
    List<String> newArgs = new ArrayList<>(Arrays.asList(argv));
    String path = extractOutputPath(newArgs);

    MaskedClassLoader.preRegisterFileManager(context);

    MultiTaskListener.instance(context)
        .add(new RefasterRuleCompilerAnalyzer(context, FileSystems.getDefault().getPath(path)));

    return Iterables.toArray(newArgs, String.class);
  }

  private String extractOutputPath(List<String> args) {
    Iterator<String> itr = args.iterator();
    String path = null;
    while (itr.hasNext()) {
      if (itr.next().equals("--out")) {
        itr.remove();
        path = itr.next();
        itr.remove();
        return path;
      }
    }

    throw new IllegalArgumentException("No --out specified");
  }
}
