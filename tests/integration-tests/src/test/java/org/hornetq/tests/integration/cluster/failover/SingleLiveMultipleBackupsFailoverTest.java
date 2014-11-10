/*
 * Copyright 2005-2014 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.hornetq.tests.integration.cluster.failover;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hornetq.api.config.HornetQDefaultConfiguration;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.core.client.impl.ClientSessionFactoryInternal;
import org.hornetq.core.client.impl.ServerLocatorImpl;
import org.hornetq.core.client.impl.Topology;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.ha.ReplicatedPolicyConfiguration;
import org.hornetq.core.config.ha.SharedStoreMasterPolicyConfiguration;
import org.hornetq.core.config.ha.SharedStoreSlavePolicyConfiguration;
import org.hornetq.core.server.NodeManager;
import org.hornetq.core.server.impl.InVMNodeManager;
import org.hornetq.tests.integration.IntegrationTestLogger;
import org.hornetq.tests.integration.cluster.util.SameProcessHornetQServer;
import org.hornetq.tests.integration.cluster.util.TestableServer;
import org.junit.After;
import org.junit.Test;

/**
 */
public class SingleLiveMultipleBackupsFailoverTest extends MultipleBackupsFailoverTestBase
{

   protected Map<Integer, TestableServer> servers = new HashMap<Integer, TestableServer>();
   protected ServerLocatorImpl locator;
   private NodeManager nodeManager;
   final boolean sharedStore = true;
   IntegrationTestLogger log = IntegrationTestLogger.LOGGER;

   public void _testLoop() throws Exception
   {
      for (int i = 0; i < 100; i++)
      {
         log.info("#test " + i);
         testMultipleFailovers();
         tearDown();
         setUp();
      }
   }

   @Test
   public void testMultipleFailovers() throws Exception
   {
      nodeManager = new InVMNodeManager(!sharedStore);
      createLiveConfig(0);
      createBackupConfig(0, 1, 0, 2, 3, 4, 5);
      createBackupConfig(0, 2, 0, 1, 3, 4, 5);
      createBackupConfig(0, 3, 0, 1, 2, 4, 5);
      createBackupConfig(0, 4, 0, 1, 2, 3, 5);
      createBackupConfig(0, 5, 0, 1, 2, 3, 4);

      servers.get(0).start();
      waitForServer(servers.get(0).getServer());
      servers.get(1).start();
      waitForServer(servers.get(1).getServer());
      servers.get(2).start();
      servers.get(3).start();
      servers.get(4).start();
      servers.get(5).start();

      locator = (ServerLocatorImpl) getServerLocator(0);

      Topology topology = locator.getTopology();

      // for logging and debugging
      topology.setOwner("testMultipleFailovers");

      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setBlockOnAcknowledge(true);
      locator.setReconnectAttempts(-1);
      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);
      int backupNode;
      ClientSession session = sendAndConsume(sf, true);

      log.info("failing node 0");
      servers.get(0).crash(session);

      session.close();
      backupNode = waitForNewLive(5, true, servers, 1, 2, 3, 4, 5);
      session = sendAndConsume(sf, false);
      log.info("failing node " + backupNode);
      servers.get(backupNode).crash(session);

      session.close();
      backupNode = waitForNewLive(5, true, servers, 1, 2, 3, 4, 5);
      session = sendAndConsume(sf, false);
      log.info("failing node " + backupNode);
      servers.get(backupNode).crash(session);

      session.close();
      backupNode = waitForNewLive(5, true, servers, 1, 2, 3, 4, 5);
      session = sendAndConsume(sf, false);
      log.info("failing node " + backupNode);
      servers.get(backupNode).crash(session);

      session.close();
      backupNode = waitForNewLive(5, true, servers, 1, 2, 3, 4, 5);
      session = sendAndConsume(sf, false);
      log.info("failing node " + backupNode);
      servers.get(backupNode).crash(session);

      session.close();
      backupNode = waitForNewLive(5, false, servers, 1, 2, 3, 4, 5);
      session = sendAndConsume(sf, false);
      session.close();
      servers.get(backupNode).stop();

      locator.close();
   }

   protected void createBackupConfig(int liveNode, int nodeid, int... nodes) throws Exception
   {
      TransportConfiguration backupConnector = createTransportConfiguration(isNetty(), false, generateParams(nodeid, isNetty()));

      Configuration config1 = super.createDefaultConfig()
         .clearAcceptorConfigurations()
         .addAcceptorConfiguration(createTransportConfiguration(isNetty(), true, generateParams(nodeid, isNetty())))
         .setSecurityEnabled(false)
         .setHAPolicyConfiguration(sharedStore ? new SharedStoreSlavePolicyConfiguration() : new ReplicatedPolicyConfiguration())
         .addConnectorConfiguration(backupConnector.getName(), backupConnector)
         .setBindingsDirectory(HornetQDefaultConfiguration.getDefaultBindingsDirectory() + "_" + liveNode)
         .setJournalDirectory(HornetQDefaultConfiguration.getDefaultJournalDir() + "_" + liveNode)
         .setPagingDirectory(HornetQDefaultConfiguration.getDefaultPagingDir() + "_" + liveNode)
         .setLargeMessagesDirectory(HornetQDefaultConfiguration.getDefaultLargeMessagesDir() + "_" + liveNode);

      List<String> staticConnectors = new ArrayList<String>();

      for (int node : nodes)
      {
         TransportConfiguration liveConnector = createTransportConfiguration(isNetty(), false, generateParams(node, isNetty()));
         config1.addConnectorConfiguration(liveConnector.getName(), liveConnector);
         staticConnectors.add(liveConnector.getName());
      }
      config1.addClusterConfiguration(basicClusterConnectionConfig(backupConnector.getName(), staticConnectors));

      servers.put(nodeid, new SameProcessHornetQServer(createInVMFailoverServer(true, config1, nodeManager, nodeid)));
   }

   protected void createLiveConfig(int liveNode) throws Exception
   {
      TransportConfiguration liveConnector = createTransportConfiguration(isNetty(), false, generateParams(liveNode, isNetty()));

      Configuration config0 = super.createDefaultConfig()
         .clearAcceptorConfigurations()
         .addAcceptorConfiguration(createTransportConfiguration(isNetty(), true, generateParams(liveNode, isNetty())))
         .setSecurityEnabled(false)
         .setHAPolicyConfiguration(sharedStore ? new SharedStoreMasterPolicyConfiguration() : new ReplicatedPolicyConfiguration())
         .addClusterConfiguration(basicClusterConnectionConfig(liveConnector.getName()))
         .addConnectorConfiguration(liveConnector.getName(), liveConnector)
         .setBindingsDirectory(HornetQDefaultConfiguration.getDefaultBindingsDirectory() + "_" + liveNode)
         .setJournalDirectory(HornetQDefaultConfiguration.getDefaultJournalDir() + "_" + liveNode)
         .setPagingDirectory(HornetQDefaultConfiguration.getDefaultPagingDir() + "_" + liveNode)
         .setLargeMessagesDirectory(HornetQDefaultConfiguration.getDefaultLargeMessagesDir() + "_" + liveNode);

      servers.put(liveNode, new SameProcessHornetQServer(createInVMFailoverServer(true, config0, nodeManager, liveNode)));
   }

   @Override
   protected boolean isNetty()
   {
      return false;
   }

   @Override
   @After
   public void tearDown() throws Exception
   {
      closeServerLocator(locator);
      for (TestableServer server : servers.values())
      {
         try
         {
            stopComponent(server);
         }
         catch (Exception e)
         {
            // ignore
         }
      }
      servers.clear();
      super.tearDown();
   }

}
