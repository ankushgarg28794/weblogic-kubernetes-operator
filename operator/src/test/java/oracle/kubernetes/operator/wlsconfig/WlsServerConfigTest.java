// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.junit.Test;

public class WlsServerConfigTest {

  static final int LISTEN_PORT = 8001;
  static final int SSL_LISTEN_PORT = 8002;
  static final int ADMIN_PORT = 9002;
  static final int NAP_ADMIN_PORT = 8082;
  static final int NAP_NON_ADMIN_PORT = 8081;

  @Test
  public void verify_getLocalAdminProtocolChannelPort_returnsListenPort() {
    WlsServerConfig wlsServerConfig = createConfigWithOnlyListenPort();
    assertThat(wlsServerConfig.getLocalAdminProtocolChannelPort(), is(LISTEN_PORT));
    assertThat(wlsServerConfig.isLocalAdminProtocolChannelSecure(), is(false));
  }

  @Test
  public void verify_getLocalAdminProtocolChannelPort_returnsSslListenPort() {
    WlsServerConfig wlsServerConfig = createConfigWithListenPortAndSslListenPort();
    assertThat(wlsServerConfig.getLocalAdminProtocolChannelPort(), is(SSL_LISTEN_PORT));
    assertThat(wlsServerConfig.isLocalAdminProtocolChannelSecure(), is(true));
  }

  @Test
  public void verify_getLocalAdminProtocolChannelPort_returnsAdminPort() {
    WlsServerConfig wlsServerConfig = createConfigWithAllListenPorts();
    assertThat(wlsServerConfig.getLocalAdminProtocolChannelPort(), is(ADMIN_PORT));
    assertThat(wlsServerConfig.isLocalAdminProtocolChannelSecure(), is(true));
  }

  @Test
  public void verify_getLocalAdminProtocolChannelPort_withAdminNAP_returnsNapAdminPort() {
    WlsServerConfig wlsServerConfig = createConfigWithAdminNAP();
    assertThat(wlsServerConfig.getLocalAdminProtocolChannelPort(), is(NAP_ADMIN_PORT));
    assertThat(wlsServerConfig.isLocalAdminProtocolChannelSecure(), is(true));
  }

  @Test
  public void verify_getLocalAdminProtocolChannelPort_withNonAdminNAP_returnsAdminPort() {
    WlsServerConfig wlsServerConfig = createConfigWithNonAdminNAP();
    assertThat(wlsServerConfig.getLocalAdminProtocolChannelPort(), is(ADMIN_PORT));
    assertThat(wlsServerConfig.isLocalAdminProtocolChannelSecure(), is(true));
  }

  WlsServerConfig createConfigWithOnlyListenPort() {
    WlsServerConfig wlsServerConfig = new WlsServerConfig();
    wlsServerConfig.setListenPort(LISTEN_PORT);
    return wlsServerConfig;
  }

  WlsServerConfig createConfigWithListenPortAndSslListenPort() {
    WlsServerConfig wlsServerConfig = createConfigWithOnlyListenPort();
    wlsServerConfig.setSslListenPort(SSL_LISTEN_PORT);
    return wlsServerConfig;
  }

  WlsServerConfig createConfigWithAllListenPorts() {
    WlsServerConfig wlsServerConfig = createConfigWithListenPortAndSslListenPort();
    wlsServerConfig.setAdminPort(ADMIN_PORT);
    return wlsServerConfig;
  }

  WlsServerConfig createConfigWithAdminNAP() {
    WlsServerConfig wlsServerConfig = createConfigWithAllListenPorts();
    wlsServerConfig.addNetworkAccessPoint(
        new NetworkAccessPoint("admin-channel", "admin", NAP_ADMIN_PORT, null));
    return wlsServerConfig;
  }

  WlsServerConfig createConfigWithNonAdminNAP() {
    WlsServerConfig wlsServerConfig = createConfigWithAllListenPorts();
    wlsServerConfig.addNetworkAccessPoint(
        new NetworkAccessPoint("non-admin-channel", "t3", NAP_NON_ADMIN_PORT, null));
    return wlsServerConfig;
  }
}
