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
package org.apache.activemq6.tests.integration.cluster.failover;

import java.util.concurrent.TimeUnit;

import org.apache.activemq6.api.core.SimpleString;
import org.apache.activemq6.api.core.TransportConfiguration;
import org.apache.activemq6.api.core.client.ClientConsumer;
import org.apache.activemq6.api.core.client.ClientMessage;
import org.apache.activemq6.api.core.client.ClientProducer;
import org.apache.activemq6.api.core.client.ClientSession;
import org.apache.activemq6.api.core.client.ClientSessionFactory;
import org.apache.activemq6.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq6.core.client.impl.ServerLocatorInternal;
import org.apache.activemq6.core.config.ha.SharedStoreMasterPolicyConfiguration;
import org.apache.activemq6.core.config.ha.SharedStoreSlavePolicyConfiguration;
import org.apache.activemq6.core.server.impl.InVMNodeManager;
import org.apache.activemq6.jms.client.HornetQTextMessage;
import org.apache.activemq6.tests.integration.cluster.util.TestableServer;
import org.apache.activemq6.tests.util.CountDownSessionFailureListener;
import org.apache.activemq6.tests.util.TransportConfigurationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:andy.taylor@jboss.com">Andy Taylor</a>
 *         Date: Dec 21, 2010
 *         Time: 12:04:16 PM
 */
public class FailBackManualTest extends FailoverTestBase
{
   private ServerLocatorInternal locator;

   @Override
   @Before
   public void setUp() throws Exception
   {
      super.setUp();
      locator = getServerLocator();
   }

   @Test
   public void testNoAutoFailback() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setFailoverOnInitialConnection(true);
      locator.setReconnectAttempts(-1);
      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sendAndConsume(sf, true);

      CountDownSessionFailureListener listener = new CountDownSessionFailureListener(1, session);

      session.addFailureListener(listener);

      backupServer.stop();

      liveServer.crash();

      backupServer.start();

      assertTrue(listener.getLatch()
                    .await(5, TimeUnit.SECONDS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);

      session.removeFailureListener(listener);

      Thread t = new Thread(new ServerStarter(liveServer));

      t.start();

      waitForRemoteBackup(sf, 10, false, backupServer.getServer());

      assertTrue(backupServer.isStarted());

      backupServer.crash();

      waitForServer(liveServer.getServer());

      assertTrue(liveServer.isStarted());

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }


   @Override
   protected void createConfigs() throws Exception
   {
      nodeManager = new InVMNodeManager(false);
      TransportConfiguration liveConnector = getConnectorTransportConfiguration(true);
      TransportConfiguration backupConnector = getConnectorTransportConfiguration(false);

      backupConfig = super.createDefaultConfig()
         .clearAcceptorConfigurations()
         .addAcceptorConfiguration(getAcceptorTransportConfiguration(false))
         .setSecurityEnabled(false)
         .setHAPolicyConfiguration(new SharedStoreSlavePolicyConfiguration()
                                      .setAllowFailBack(false))
         .addConnectorConfiguration(liveConnector.getName(), liveConnector)
         .addConnectorConfiguration(backupConnector.getName(), backupConnector)
         .addClusterConfiguration(basicClusterConnectionConfig(backupConnector.getName(), liveConnector.getName()));

      backupServer = createTestableServer(backupConfig);

      liveConfig = super.createDefaultConfig()
         .clearAcceptorConfigurations()
         .addAcceptorConfiguration(getAcceptorTransportConfiguration(true))
         .setSecurityEnabled(false)
         .setHAPolicyConfiguration(new SharedStoreMasterPolicyConfiguration())
         .addConnectorConfiguration(liveConnector.getName(), liveConnector)
         .addConnectorConfiguration(backupConnector.getName(), backupConnector)
         .addClusterConfiguration(basicClusterConnectionConfig(liveConnector.getName(), backupConnector.getName()));

      liveServer = createTestableServer(liveConfig);
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live)
   {
      return TransportConfigurationUtils.getInVMAcceptor(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live)
   {
      return TransportConfigurationUtils.getInVMConnector(live);
   }


   private ClientSession sendAndConsume(final ClientSessionFactory sf, final boolean createQueue) throws Exception
   {
      ClientSession session = sf.createSession(false, true, true);

      if (createQueue)
      {
         session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, false);
      }

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 1000;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(HornetQTextMessage.TYPE,
                                                       false,
                                                       0,
                                                       System.currentTimeMillis(),
                                                       (byte) 1);
         message.putIntProperty(new SimpleString("count"), i);
         message.getBodyBuffer()
            .writeString("aardvarks");
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message2 = consumer.receive();

         Assert.assertEquals("aardvarks", message2.getBodyBuffer()
            .readString());

         Assert.assertEquals(i, message2.getObjectProperty(new SimpleString("count")));

         message2.acknowledge();
      }

      ClientMessage message3 = consumer.receiveImmediate();

      Assert.assertNull(message3);

      return session;
   }

   /**
    * @param i
    * @param message
    * @throws Exception
    */
   @Override
   protected void setBody(final int i, final ClientMessage message)
   {
      message.getBodyBuffer()
         .writeString("message" + i);
   }

   static class ServerStarter implements Runnable
   {
      private final TestableServer server;

      public ServerStarter(TestableServer server)
      {
         this.server = server;
      }

      public void run()
      {
         try
         {
            server.start();
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }
      }
   }
}
