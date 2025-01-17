// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Kubernetes pods and services associated with a single WebLogic server. */
class ServerKubernetesObjects {
  private final AtomicReference<V1Pod> pod = new AtomicReference<>(null);
  private final AtomicBoolean isPodBeingDeleted = new AtomicBoolean(false);
  private final AtomicReference<LastKnownStatus> lastKnownStatus = new AtomicReference<>(null);
  private final AtomicReference<V1Service> service = new AtomicReference<>(null);
  private final AtomicReference<V1Service> externalService = new AtomicReference<>();

  ServerKubernetesObjects() {}

  /**
   * The Pod.
   *
   * @return Pod
   */
  AtomicReference<V1Pod> getPod() {
    return pod;
  }

  /**
   * Flag indicating if the operator is deleting this pod.
   *
   * @return true, if operator is deleting this pod
   */
  AtomicBoolean isPodBeingDeleted() {
    return isPodBeingDeleted;
  }

  /**
   * Managed server status.
   *
   * @return Status
   */
  AtomicReference<LastKnownStatus> getLastKnownStatus() {
    return lastKnownStatus;
  }

  /**
   * The Service.
   *
   * @return Service
   */
  AtomicReference<V1Service> getService() {
    return service;
  }

  AtomicReference<V1Service> getExternalService() {
    return externalService;
  }
}
