// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import oracle.kubernetes.weblogic.domain.model.Shutdown;

public class PodHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  static final long DEFAULT_ADDITIONAL_DELETE_TIME = 10;

  private PodHelper() {}

  /**
   * Creates an admin server pod resource, based on the specified packet
   *
   * @param packet a packet describing the domain model and topology.
   * @return an appropriate Kubernetes resource
   */
  public static V1Pod createAdminServerPodModel(Packet packet) {
    return new AdminPodStepContext(null, packet).createPodModel();
  }

  /**
   * Creates a managed server pod resource, based on the specified packet
   *
   * @param packet a packet describing the domain model and topology.
   * @return an appropriate Kubernetes resource
   */
  static V1Pod createManagedServerPodModel(Packet packet) {
    return new ManagedPodStepContext(null, packet).createPodModel();
  }

  public static boolean isReady(V1Pod pod) {
    boolean ready = getReadyStatus(pod);
    if (ready) {
      LOGGER.info(MessageKeys.POD_IS_READY, pod.getMetadata().getName());
    }
    return ready;
  }

  public static boolean getReadyStatus(V1Pod pod) {
    V1PodStatus status = pod.getStatus();
    if (status != null) {
      if ("Running".equals(status.getPhase())) {
        List<V1PodCondition> conds = status.getConditions();
        if (conds != null) {
          for (V1PodCondition cond : conds) {
            if ("Ready".equals(cond.getType())) {
              if ("True".equals(cond.getStatus())) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  public static boolean isDeleting(V1Pod pod) {
    V1ObjectMeta meta = pod.getMetadata();
    if (meta != null) {
      return meta.getDeletionTimestamp() != null;
    }
    return false;
  }

  public static boolean isFailed(V1Pod pod) {
    V1PodStatus status = pod.getStatus();
    if (status != null) {
      if ("Failed".equals(status.getPhase())) {
        LOGGER.severe(MessageKeys.POD_IS_FAILED, pod.getMetadata().getName());
        return true;
      }
    }
    return false;
  }

  public static String getPodDomainUID(V1Pod pod) {
    V1ObjectMeta meta = pod.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.DOMAINUID_LABEL);
    }
    return null;
  }

  public static String getPodServerName(V1Pod pod) {
    V1ObjectMeta meta = pod.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.SERVERNAME_LABEL);
    }
    return null;
  }

  static class AdminPodStepContext extends PodStepContext {
    static final String INTERNAL_OPERATOR_CERT_ENV = "INTERNAL_OPERATOR_CERT";

    AdminPodStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet);

      init();
    }

    @Override
    ServerSpec getServerSpec() {
      return getDomain().getAdminServerSpec();
    }

    @Override
    Integer getDefaultPort() {
      return getAsPort();
    }

    @Override
    String getServerName() {
      return getAsName();
    }

    @Override
    Step createNewPod(Step next) {
      return createPod(next);
    }

    @Override
    Step replaceCurrentPod(Step next) {
      return createCyclePodStep(next);
    }

    @Override
    String getPodCreatedMessageKey() {
      return MessageKeys.ADMIN_POD_CREATED;
    }

    @Override
    String getPodExistsMessageKey() {
      return MessageKeys.ADMIN_POD_EXISTS;
    }

    @Override
    String getPodPatchedMessageKey() {
      return MessageKeys.ADMIN_POD_PATCHED;
    }

    @Override
    String getPodReplacedMessageKey() {
      return MessageKeys.ADMIN_POD_REPLACED;
    }

    @Override
    V1Pod withNonHashedElements(V1Pod pod) {
      V1Pod v1Pod = super.withNonHashedElements(pod);
      getContainer(v1Pod).ifPresent(c -> c.addEnvItem(internalCertEnvValue()));

      return v1Pod;
    }

    private V1EnvVar internalCertEnvValue() {
      return new V1EnvVar().name(INTERNAL_OPERATOR_CERT_ENV).value(getInternalOperatorCertFile());
    }

    @Override
    protected V1PodSpec createSpec(TuningParameters tuningParameters) {
      return super.createSpec(tuningParameters).hostname(getPodName());
    }

    @Override
    List<V1EnvVar> getConfiguredEnvVars(TuningParameters tuningParameters) {
      List<V1EnvVar> vars = createCopy(getServerSpec().getEnvironmentVariables());
      overrideContainerWeblogicEnvVars(vars);
      return vars;
    }

    @Override
    protected Map<String, String> getPodLabels() {
      return getServerSpec().getPodLabels();
    }

    @Override
    protected Map<String, String> getPodAnnotations() {
      return getServerSpec().getPodAnnotations();
    }

    private String getInternalOperatorCertFile() {
      return Certificates.getOperatorInternalCertificateData();
    }
  }

  /**
   * Factory for {@link Step} that creates admin server pod.
   *
   * @param next Next processing step
   * @return Step for creating admin server pod
   */
  public static Step createAdminPodStep(Step next) {
    return new AdminPodStep(next);
  }

  static class AdminPodStep extends Step {

    AdminPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      PodStepContext context = new AdminPodStepContext(this, packet);

      return doNext(context.verifyPersistentVolume(context.verifyPod(getNext())), packet);
    }
  }

  static void addToPacket(Packet packet, PodAwaiterStepFactory pw) {
    packet
        .getComponents()
        .put(
            ProcessingConstants.PODWATCHER_COMPONENT_NAME,
            Component.createFor(PodAwaiterStepFactory.class, pw));
  }

  static PodAwaiterStepFactory getPodAwaiterStepFactory(Packet packet) {
    return packet.getSPI(PodAwaiterStepFactory.class);
  }

  /**
   * Factory for {@link Step} that creates managed server pod.
   *
   * @param next Next processing step
   * @return Step for creating managed server pod
   */
  public static Step createManagedPodStep(Step next) {
    return new ManagedPodStep(next);
  }

  static class ManagedPodStepContext extends PodStepContext {

    private final String clusterName;
    private final Packet packet;

    ManagedPodStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet);
      this.packet = packet;
      clusterName = (String) packet.get(ProcessingConstants.CLUSTER_NAME);

      init();
    }

    @Override
    ServerSpec getServerSpec() {
      return getDomain().getServer(getServerName(), getClusterName());
    }

    @Override
    protected Map<String, String> getPodLabels() {
      return getServerSpec().getPodLabels();
    }

    @Override
    protected Map<String, String> getPodAnnotations() {
      return getServerSpec().getPodAnnotations();
    }

    @Override
    boolean isLocalAdminProtocolChannelSecure() {
      return scan.isLocalAdminProtocolChannelSecure();
    }

    @Override
    Integer getLocalAdminProtocolChannelPort() {
      return scan.getLocalAdminProtocolChannelPort();
    }

    @Override
    Integer getDefaultPort() {
      return scan.getListenPort();
    }

    @Override
    String getServerName() {
      return scan.getName();
    }

    @Override
    // let the pod rolling step update the pod
    Step replaceCurrentPod(Step next) {
      synchronized (packet) {
        @SuppressWarnings("unchecked")
        Map<String, Step.StepAndPacket> rolling =
            (Map<String, Step.StepAndPacket>) packet.get(ProcessingConstants.SERVERS_TO_ROLL);
        if (rolling != null) {
          rolling.put(
              getServerName(),
              new Step.StepAndPacket(
                  DomainStatusUpdater.createProgressingStep(
                      DomainStatusUpdater.MANAGED_SERVERS_STARTING_PROGRESS_REASON,
                      false,
                      createCyclePodStep(next)),
                  packet.clone()));
        }
      }
      return null;
    }

    @Override
    Step createNewPod(Step next) {
      return DomainStatusUpdater.createProgressingStep(
          DomainStatusUpdater.MANAGED_SERVERS_STARTING_PROGRESS_REASON, false, createPod(next));
    }

    @Override
    String getPodCreatedMessageKey() {
      return MessageKeys.MANAGED_POD_CREATED;
    }

    @Override
    String getPodExistsMessageKey() {
      return MessageKeys.MANAGED_POD_EXISTS;
    }

    @Override
    String getPodPatchedMessageKey() {
      return MessageKeys.MANAGED_POD_PATCHED;
    }

    @Override
    protected String getPodReplacedMessageKey() {
      return MessageKeys.MANAGED_POD_REPLACED;
    }

    @Override
    protected V1ObjectMeta createMetadata() {
      V1ObjectMeta metadata = super.createMetadata();
      if (getClusterName() != null) {
        metadata.putLabelsItem(LabelConstants.CLUSTERNAME_LABEL, getClusterName());
      }
      return metadata;
    }

    private String getClusterName() {
      return clusterName;
    }

    @Override
    protected List<String> getContainerCommand() {
      return new ArrayList<>(super.getContainerCommand());
    }

    @Override
    @SuppressWarnings("unchecked")
    List<V1EnvVar> getConfiguredEnvVars(TuningParameters tuningParameters) {
      List<V1EnvVar> envVars = createCopy((List<V1EnvVar>) packet.get(ProcessingConstants.ENVVARS));

      List<V1EnvVar> vars = new ArrayList<>();
      if (envVars != null) {
        vars.addAll(envVars);
      }
      overrideContainerWeblogicEnvVars(vars);
      return vars;
    }
  }

  static class ManagedPodStep extends Step {
    ManagedPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      ManagedPodStepContext context = new ManagedPodStepContext(this, packet);

      return doNext(context.verifyPod(getNext()), packet);
    }
  }

  /**
   * Factory for {@link Step} that deletes server pod.
   *
   * @param serverName the name of the server whose pod is to be deleted
   * @param next Next processing step
   * @return Step for deleting server pod
   */
  public static Step deletePodStep(String serverName, Step next) {
    return new DeletePodStep(serverName, next);
  }

  private static class DeletePodStep extends Step {
    private final String serverName;

    DeletePodStep(String serverName, Step next) {
      super(next);
      this.serverName = serverName;
    }

    @Override
    public NextAction apply(Packet packet) {

      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      V1Pod oldPod = info.getServerPod(serverName);

      long gracePeriodSeconds = Shutdown.DEFAULT_TIMEOUT;
      String clusterName = null;
      if (oldPod != null) {
        Map<String, String> labels = oldPod.getMetadata().getLabels();
        if (labels != null) {
          clusterName = labels.get(LabelConstants.CLUSTERNAME_LABEL);
        }

        ServerSpec serverSpec = info.getDomain().getServer(serverName, clusterName);
        if (serverSpec != null) {
          // We add a 10 second fudge factor here to account for the fact that WLST takes
          // ~6 seconds to start, so along with any other delay in connecting and issuing
          // the shutdown, the actual server instance has the full configured timeout to
          // gracefully shutdown before the container is destroyed by this timeout.
          // We will remove this fudge factor when the operator connects via REST to shutdown
          // the server instance.
          gracePeriodSeconds =
              serverSpec.getShutdown().getTimeoutSeconds() + DEFAULT_ADDITIONAL_DELETE_TIME;
        }

        String name = oldPod.getMetadata().getName();
        info.setServerPodBeingDeleted(serverName, Boolean.TRUE);
        return doNext(deletePod(name, info.getNamespace(), gracePeriodSeconds, getNext()), packet);
      } else {
        return doNext(packet);
      }
    }

    private Step deletePod(String name, String namespace, long gracePeriodSeconds, Step next) {

      Step conflictStep =
          new CallBuilder()
              .readPodAsync(
                  name,
                  namespace,
                  new DefaultResponseStep<>(next) {
                    @Override
                    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
                      V1Pod pod = callResponse.getResult();

                      if (pod != null && !PodHelper.isDeleting(pod)) {
                        // pod still needs to be deleted
                        return doNext(DeletePodStep.this, packet);
                      }
                      return super.onSuccess(packet, callResponse);
                    }
                  });

      V1DeleteOptions deleteOptions = new V1DeleteOptions().gracePeriodSeconds(gracePeriodSeconds);
      return new CallBuilder()
          .deletePodAsync(
              name, namespace, deleteOptions, new DefaultResponseStep<>(conflictStep, next));
    }
  }

  static List<V1EnvVar> createCopy(List<V1EnvVar> envVars) {
    ArrayList<V1EnvVar> copy = new ArrayList<>();
    if (envVars != null) {
      for (V1EnvVar envVar : envVars) {
        copy.add(new V1EnvVar().name(envVar.getName()).value(envVar.getValue()));
      }
    }
    return copy;
  }
}
