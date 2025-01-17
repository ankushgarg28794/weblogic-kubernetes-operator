// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Map;
import oracle.kubernetes.operator.BaseTest;

/** JRF Domain class with all the utility methods */
public class JRFDomain extends Domain {

  /**
   * JRFDomain constructor
   *
   * @param inputYaml - jrf domain input yaml file, which should contain the properties used for jrf
   *     domain creation
   * @throws Exception - if any error occurs
   */
  public JRFDomain(String inputYaml) throws Exception {
    // read input domain yaml to test
    this(TestUtils.loadYaml(inputYaml));
  }

  /**
   * JRFDomain constructor
   *
   * @param inputDomainMap - jrf domain input properties map, which should contain the properties
   *     used for domain creation
   * @throws Exception - if any error occurs
   */
  public JRFDomain(Map<String, Object> inputDomainMap) throws Exception {
    this(inputDomainMap, false);
  }

  public JRFDomain(Map<String, Object> inputDomainMap, boolean adminPortEnabled) throws Exception {
    initialize(inputDomainMap);
    updateDomainMapForJRF(adminPortEnabled);
    createPV();
    createSecret();
    createRcuSecret();
    generateInputYaml();
    callCreateDomainScript(userProjectsDir);
    createLoadBalancer();
  }

  /**
   * update the domainMap with jrf specific information
   *
   * @param adminPortEnabled - whether the adminPortEnabled, value true or false
   * @throws Exception - if any error occurs
   */
  private void updateDomainMapForJRF(boolean adminPortEnabled) throws Exception {
    // jrf specific input parameter
    domainMap.put(
        "image",
        DBUtils.DEFAULT_FMWINFRA_DOCKER_IMAGENAME + ":" + DBUtils.DEFAULT_FMWINFRA_DOCKER_IMAGETAG);

    if (System.getenv("IMAGE_PULL_SECRET_FMWINFRA") != null) {
      domainMap.put("imagePullSecretName", System.getenv("IMAGE_PULL_SECRET_FMWINFRA"));
    } else {
      domainMap.put("imagePullSecretName", "ocir-store");
    }

    // update create-domain-script.sh if adminPortEnabled is true
    if (adminPortEnabled) {
      String createDomainScript =
          BaseTest.getResultDir()
              + "/samples/scripts/create-fmw-infrastructure-domain/wlst/create-domain-script.sh";
      TestUtils.replaceStringInFile(
          createDomainScript,
          "-managedNameBase ",
          "-adminPortEnabled true -administrationPort 9002 -managedNameBase ");
    }
  }

  /**
   * create rcu secret
   *
   * @throws Exception - if any error occurs
   */
  private void createRcuSecret() throws Exception {
    RcuSecret rucSecret =
        new RcuSecret(
            domainNS,
            domainMap.getOrDefault("secretName", domainUid + "-rcu-credentials").toString(),
            DBUtils.DEFAULT_RCU_SCHEMA_USERNAME,
            DBUtils.DEFAULT_RCU_SCHEMA_PASSWORD,
            DBUtils.DEFAULT_RCU_SYS_USERNAME,
            DBUtils.DEFAULT_RCU_SYS_PASSWORD);
    domainMap.put("rcuCredentialsSecret", rucSecret.getSecretName());
    final String labelCmd =
        String.format(
            "kubectl label secret %s -n %s weblogic.domainUID=%s weblogic.domainName=%s",
            rucSecret.getSecretName(), domainNS, domainUid, domainUid);
    logger.info("running command " + labelCmd);
    TestUtils.exec(labelCmd);
  }
}
