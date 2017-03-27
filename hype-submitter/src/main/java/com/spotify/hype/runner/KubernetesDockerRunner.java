/*-
 * -\-\-
 * Spotify Styx Scheduler Service
 * --
 * Copyright (C) 2016 Spotify AB
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

package com.spotify.hype.runner;

import com.google.common.collect.ImmutableList;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodFluent;
import io.fabric8.kubernetes.api.model.PodSpecFluent;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ClientPodResource;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A {@link DockerRunner} implementation that submits container executions to a Kubernetes cluster.
 */
class KubernetesDockerRunner implements DockerRunner {

  private static final String NAMESPACE = "default";
  private static final String HYPE_RUN = "hype-run";
  private static final String ALPHA_NUMERIC_STRING = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final String EXECUTION_ID = "HYPE_EXECUTION_ID";
  private static final int POLL_PODS_INTERVAL_SECONDS = 60;

  private final KubernetesClient client;

  KubernetesDockerRunner(KubernetesClient client) {
    this.client = Objects.requireNonNull(client).inNamespace(NAMESPACE);
  }

  @Override
  public Optional<URI> run(RunSpec runSpec) throws IOException {
    try {
      final Pod pod = client.pods().create(createPod(runSpec));
      final String podName = pod.getMetadata().getName();
      Optional<URI> uri = blockUntilComplete(podName);
      client.pods().withName(podName).delete();
      return uri;
    } catch (KubernetesClientException kce) {
      throw new IOException("Failed to create Kubernetes pod", kce);
    } catch (InterruptedException e) {
      throw new IOException("Interrupted while blocking", e);
    }
  }

  private static Pod createPod(RunSpec runSpec) {
    final String imageWithTag = runSpec.imageName().contains(":")
        ? runSpec.imageName()
        : runSpec.imageName() + ":latest";

    final String podName = HYPE_RUN + "-" + randomAlphaNumeric(16);

    // inject environment variables
    EnvVar envVarExecution = new EnvVar();
    envVarExecution.setName(EXECUTION_ID);
    envVarExecution.setValue(podName);

    PodBuilder podBuilder = new PodBuilder()
        .withNewMetadata()
        .withName(podName)
        .endMetadata();
    PodFluent.SpecNested<PodBuilder> spec = podBuilder.withNewSpec()
        .withRestartPolicy("Never");
    PodSpecFluent.ContainersNested<PodFluent.SpecNested<PodBuilder>> container = spec
        .addNewContainer()
            .withName(HYPE_RUN)
            .withImage(imageWithTag)
            .withArgs(ImmutableList.of(runSpec.stagingLocation(), runSpec.functionFile()))
            .withEnv(envVarExecution);

    RunSpec.Secret secret = runSpec.secret();
    spec = spec.addNewVolume()
        .withName(secret.name())
        .withNewSecret()
        .withSecretName(secret.name())
        .endSecret()
        .endVolume();
    container = container
        .addToVolumeMounts(new VolumeMount(secret.mountPath(), secret.name(), true));

    container.endContainer();
    return spec.endSpec().build();
  }

  private Optional<URI> blockUntilComplete(final String podName) throws InterruptedException {
    LOG.debug("Checking running statuses");

    while (true) {
      final ClientPodResource<Pod, DoneablePod> pod = client.pods().withName(podName);
      final PodStatus status = pod.get().getStatus();

      switch (status.getPhase()) {
        case "Succeeded":
          LOG.info("Kubernetes pod {} exited with status {}", podName, status.getPhase());

          final Optional<ContainerStatus> containerStatus = status.getContainerStatuses().stream()
              .filter(c -> HYPE_RUN.equals(c.getName()))
              .findFirst();

          final Optional<String> terminated = containerStatus
              .flatMap(s -> Optional.ofNullable(s.getState().getTerminated()))
              .flatMap(t -> Optional.ofNullable(t.getMessage()));

          if (terminated.isPresent()) {
            String message = terminated.get();
            LOG.info("Got termination message: {}", message);
            return Optional.of(URI.create(message));
          }
          break;

        case "Failed":
          return Optional.empty();

        default:
          break;
      }
      Thread.sleep(TimeUnit.SECONDS.toMillis(POLL_PODS_INTERVAL_SECONDS));
    }
  }

  @Override
  public void close() throws IOException {
    client.close();
  }

  private static String randomAlphaNumeric(int count) {
    StringBuilder builder = new StringBuilder();
    while (count-- != 0) {
      int character = (int)(Math.random() * ALPHA_NUMERIC_STRING.length());
      builder.append(ALPHA_NUMERIC_STRING.charAt(character));
    }
    return builder.toString();
  }
}
