/*-
 * -\-\-
 * hype-submitter
 * --
 * Copyright (C) 2016 - 2017 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

package com.spotify.hype;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Throwables;
import com.spotify.hype.util.Fn;
import io.rouz.flo.Task;
import io.rouz.flo.TaskContext;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Test {

  public static void main(String[] args) {
    final ClasspathInspector classpathInspector = new LocalClasspathInspector(Test.class);

    final List<Path> files = classpathInspector.localClasspathJars();
    final Task<String> task = Task.named("foo").ofType(String.class)
        .process(() -> "hello");

    Fn<?> fn = () -> {
      files.forEach(file -> System.out.println("running in continuation " + file));

      CompletableFuture<String> future = new CompletableFuture<>();
      TaskContext.inmem().evaluate(task).consume(future::complete);
      final String value;
      try {
        value = future.get();
      } catch (InterruptedException | ExecutionException e) {
        throw Throwables.propagate(e);
      }

      String cwd = System.getProperty("user.dir");
      System.out.println("cwd = " + cwd);
      System.out.println("value = " + value);
      return value;
    };

    final Storage storage = StorageOptions.getDefaultInstance().getService();
    final Submitter submitter = new Submitter(storage, args[0], classpathInspector);

    StagedContinuation stagedContinuation = submitter.stageContinuation(fn);
    System.out.println("stage args " + stagedContinuation.stageLocation() + " " +
                       stagedContinuation.continuationFileName());
  }
}
