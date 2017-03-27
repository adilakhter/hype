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

import static com.spotify.hype.ContainerEngineCluster.containerEngineCluster;
import static com.spotify.hype.runner.RunSpec.Secret.secret;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.spotify.hype.runner.RunSpec;
import com.spotify.hype.util.Fn;
import java.util.stream.Collectors;

public class Example {

  private static final ClasspathInspector CPI = ClasspathInspector.forClass(Example.class);

  public static void main(String[] args) {
    final Record record = new Record("hello", 42);

    Fn<Record> fn = () -> {
      String cwd = System.getProperty("user.dir");
      System.out.println("cwd = " + cwd);
      System.out.println("record = " + record);

      String env = System.getenv().entrySet().stream()
          .map(e -> e.getKey() + "=" + e.getValue())
          .collect(Collectors.joining(", "));

      return new Record(record.foo + " world in " + env, record.bar + 100);
    };

    final Storage storage = StorageOptions.getDefaultInstance().getService();
    final ContainerEngineCluster cluster = containerEngineCluster("datawhere-test", "us-east1-d", "hype-test");
    final RunSpec.Secret secret = secret("gcp-key", "/etc/gcloud");
    final Submitter submitter = Submitter.create(storage, args[0], CPI, cluster, secret);

    final Record returnRecord = submitter.runOnCluster(fn);
    System.out.println("returnRecord = " + returnRecord);
  }

  private static class Record {
    final String foo;
    final int bar;

    private Record(String foo, int bar) {
      this.foo = foo;
      this.bar = bar;
    }

    @Override
    public String toString() {
      return "Record{" +
             "foo='" + foo + '\'' +
             ", bar=" + bar +
             '}';
    }
  }
}
