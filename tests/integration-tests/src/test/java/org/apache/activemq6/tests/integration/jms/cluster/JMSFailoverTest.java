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
package org.apache.activemq6.tests.integration.jms.cluster;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq6.api.core.HornetQException;
import org.apache.activemq6.api.core.Interceptor;
import org.apache.activemq6.api.core.SimpleString;
import org.apache.activemq6.api.core.TransportConfiguration;
import org.apache.activemq6.api.core.client.ClientSession;
import org.apache.activemq6.api.jms.HornetQJMSClient;
import org.apache.activemq6.api.jms.JMSFactoryType;
import org.apache.activemq6.core.client.impl.ClientSessionInternal;
import org.apache.activemq6.core.config.Configuration;
import org.apache.activemq6.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq6.core.config.ha.ReplicatedPolicyConfiguration;
import org.apache.activemq6.core.config.ha.SharedStoreMasterPolicyConfiguration;
import org.apache.activemq6.core.config.ha.SharedStoreSlavePolicyConfiguration;
import org.apache.activemq6.core.protocol.core.Packet;
import org.apache.activemq6.core.protocol.core.impl.wireformat.SessionReceiveContinuationMessage;
import org.apache.activemq6.core.remoting.impl.invm.InVMRegistry;
import org.apache.activemq6.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq6.core.server.HornetQServer;
import org.apache.activemq6.core.server.NodeManager;
import org.apache.activemq6.core.server.impl.InVMNodeManager;
import org.apache.activemq6.jms.client.HornetQConnectionFactory;
import org.apache.activemq6.jms.client.HornetQDestination;
import org.apache.activemq6.jms.client.HornetQSession;
import org.apache.activemq6.jms.server.JMSServerManager;
import org.apache.activemq6.jms.server.impl.JMSServerManagerImpl;
import org.apache.activemq6.spi.core.protocol.RemotingConnection;
import org.apache.activemq6.tests.integration.IntegrationTestLogger;
import org.apache.activemq6.tests.integration.jms.server.management.JMSUtil;
import org.apache.activemq6.tests.unit.util.InVMNamingContext;
import org.apache.activemq6.tests.util.InVMNodeManagerServer;
import org.apache.activemq6.tests.util.RandomUtil;
import org.apache.activemq6.tests.util.ServiceTestBase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * A JMSFailoverTest
 * <p/>
 * A simple test to test failover when using the JMS API.
 * Most of the failover tests are done on the Core API.
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *         <p/>
 *         Created 7 Nov 2008 11:13:39
 */
public class JMSFailoverTest extends ServiceTestBase
{
   private static final IntegrationTestLogger log = IntegrationTestLogger.LOGGER;

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   protected InVMNamingContext ctx1 = new InVMNamingContext();

   protected InVMNamingContext ctx2 = new InVMNamingContext();

   protected Configuration backupConf;

   protected Configuration liveConf;

   protected JMSServerManager liveJMSService;

   protected HornetQServer liveService;

   protected JMSServerManager backupJMSService;

   protected HornetQServer backupService;

   protected Map<String, Object> backupParams = new HashMap<String, Object>();

   private TransportConfiguration backuptc;

   private TransportConfiguration livetc;

   private TransportConfiguration liveAcceptortc;

   private TransportConfiguration backupAcceptortc;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   @Test
   public void testCreateQueue() throws Exception
   {
      liveJMSService.createQueue(true, "queue1", null, true, "/queue/queue1");
      assertNotNull(ctx1.lookup("/queue/queue1"));

      HornetQConnectionFactory jbcf = HornetQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, livetc);

      jbcf.setReconnectAttempts(-1);

      Connection conn = null;

      try
      {
         conn = JMSUtil.createConnectionAndWaitForTopology(jbcf, 2, 5);

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         ClientSession coreSession = ((HornetQSession) sess).getCoreSession();

         JMSUtil.crash(liveService, coreSession);

         assertNotNull(ctx2.lookup("/queue/queue1"));
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   @Test
   public void testCreateTopic() throws Exception
   {
      liveJMSService.createTopic(true, "topic", "/topic/t1");
      assertNotNull(ctx1.lookup("//topic/t1"));

      HornetQConnectionFactory jbcf = HornetQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, livetc);

      jbcf.setReconnectAttempts(-1);

      Connection conn = null;

      try
      {
         conn = JMSUtil.createConnectionAndWaitForTopology(jbcf, 2, 5);

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         ClientSession coreSession = ((HornetQSession) sess).getCoreSession();

         JMSUtil.crash(liveService, coreSession);

         assertNotNull(ctx2.lookup("/topic/t1"));
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   @Test
   public void testAutomaticFailover() throws Exception
   {
      HornetQConnectionFactory jbcf = HornetQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, livetc);
      jbcf.setReconnectAttempts(-1);
      jbcf.setBlockOnDurableSend(true);
      jbcf.setBlockOnNonDurableSend(true);

      // Note we set consumer window size to a value so we can verify that consumer credit re-sending
      // works properly on failover
      // The value is small enough that credits will have to be resent several time

      final int numMessages = 10;

      final int bodySize = 1000;

      jbcf.setConsumerWindowSize(numMessages * bodySize / 10);

      Connection conn = JMSUtil.createConnectionAndWaitForTopology(jbcf, 2, 5);

      MyExceptionListener listener = new MyExceptionListener();

      conn.setExceptionListener(listener);

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSession = ((HornetQSession) sess).getCoreSession();

      SimpleString jmsQueueName = new SimpleString(HornetQDestination.JMS_QUEUE_ADDRESS_PREFIX + "myqueue");

      coreSession.createQueue(jmsQueueName, jmsQueueName, null, true);

      Queue queue = sess.createQueue("myqueue");

      MessageProducer producer = sess.createProducer(queue);

      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      MessageConsumer consumer = sess.createConsumer(queue);

      byte[] body = RandomUtil.randomBytes(bodySize);

      for (int i = 0; i < numMessages; i++)
      {
         BytesMessage bm = sess.createBytesMessage();

         bm.writeBytes(body);

         producer.send(bm);
      }

      conn.start();

      JMSFailoverTest.log.info("sent messages and started connection");

      Thread.sleep(2000);

      JMSUtil.crash(liveService, ((HornetQSession) sess).getCoreSession());

      for (int i = 0; i < numMessages; i++)
      {
         JMSFailoverTest.log.info("got message " + i);

         BytesMessage bm = (BytesMessage) consumer.receive(1000);

         Assert.assertNotNull(bm);

         Assert.assertEquals(body.length, bm.getBodyLength());
      }

      TextMessage tm = (TextMessage) consumer.receiveNoWait();

      Assert.assertNull(tm);

      conn.close();

   }

   @Test
   public void testManualFailover() throws Exception
   {
      HornetQConnectionFactory jbcfLive =
         HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF,
                                                           new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      jbcfLive.setBlockOnNonDurableSend(true);
      jbcfLive.setBlockOnDurableSend(true);

      HornetQConnectionFactory jbcfBackup =
         HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF,
                                                           new TransportConfiguration(INVM_CONNECTOR_FACTORY,
                                                                                      backupParams));
      jbcfBackup.setBlockOnNonDurableSend(true);
      jbcfBackup.setBlockOnDurableSend(true);
      jbcfBackup.setInitialConnectAttempts(-1);
      jbcfBackup.setReconnectAttempts(-1);

      Connection connLive = jbcfLive.createConnection();

      MyExceptionListener listener = new MyExceptionListener();

      connLive.setExceptionListener(listener);

      Session sessLive = connLive.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSessionLive = ((HornetQSession) sessLive).getCoreSession();

      RemotingConnection coreConnLive = ((ClientSessionInternal) coreSessionLive).getConnection();

      SimpleString jmsQueueName = new SimpleString(HornetQDestination.JMS_QUEUE_ADDRESS_PREFIX + "myqueue");

      coreSessionLive.createQueue(jmsQueueName, jmsQueueName, null, true);

      Queue queue = sessLive.createQueue("myqueue");

      final int numMessages = 1000;

      MessageProducer producerLive = sessLive.createProducer(queue);

      for (int i = 0; i < numMessages; i++)
      {
         TextMessage tm = sessLive.createTextMessage("message" + i);

         producerLive.send(tm);
      }

      // Note we block on P send to make sure all messages get to server before failover

      JMSUtil.crash(liveService, coreSessionLive);

      connLive.close();

      // Now recreate on backup

      Connection connBackup = jbcfBackup.createConnection();

      Session sessBackup = connBackup.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer consumerBackup = sessBackup.createConsumer(queue);

      connBackup.start();

      for (int i = 0; i < numMessages; i++)
      {
         TextMessage tm = (TextMessage) consumerBackup.receive(1000);

         Assert.assertNotNull(tm);

         Assert.assertEquals("message" + i, tm.getText());
      }

      TextMessage tm = (TextMessage) consumerBackup.receiveNoWait();

      Assert.assertNull(tm);

      connBackup.close();
   }


   @Test
   public void testSendReceiveLargeMessages() throws Exception
   {
      SimpleString QUEUE = new SimpleString("jms.queue.somequeue");

      HornetQConnectionFactory jbcf = HornetQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, livetc, backuptc);
      jbcf.setReconnectAttempts(-1);
      jbcf.setBlockOnDurableSend(true);
      jbcf.setBlockOnNonDurableSend(true);
      jbcf.setMinLargeMessageSize(1024);
      //jbcf.setConsumerWindowSize(0);
      //jbcf.setMinLargeMessageSize(1024);


      final CountDownLatch flagAlign = new CountDownLatch(1);

      final CountDownLatch waitToKill = new CountDownLatch(1);

      final AtomicBoolean killed = new AtomicBoolean(false);

      jbcf.getServerLocator().addIncomingInterceptor(new Interceptor()
      {
         int count = 0;

         @Override
         public boolean intercept(Packet packet, RemotingConnection connection) throws HornetQException
         {

            if (packet instanceof SessionReceiveContinuationMessage)
            {
               if (count++ == 300 && !killed.get())
               {
                  System.out.println("sending countDown on latch waitToKill");
                  killed.set(true);
                  waitToKill.countDown();
               }
            }
            return true;
         }
      });


      Connection conn = JMSUtil.createConnectionAndWaitForTopology(jbcf, 2, 5);
      Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
      final ClientSession coreSession = ((HornetQSession) sess).getCoreSession();


      // The thread that will fail the server
      Thread spoilerThread = new Thread()
      {
         public void run()
         {
            flagAlign.countDown();
            // a large timeout just to help in case of debugging
            try
            {
               waitToKill.await(120, TimeUnit.SECONDS);
            }
            catch (Exception e)
            {
               e.printStackTrace();
            }

            try
            {
               System.out.println("Killing server...");

               JMSUtil.crash(liveService, coreSession);
            }
            catch (Exception e)
            {
               e.printStackTrace();
            }
         }
      };

      coreSession.createQueue(QUEUE, QUEUE, true);

      Queue queue = sess.createQueue("somequeue");

      MessageProducer producer = sess.createProducer(queue);
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      for (int i = 0; i < 100; i++)
      {
         TextMessage message = sess.createTextMessage(new String(new byte[10 * 1024]));
         producer.send(message);

         if (i % 10 == 0)
         {
            sess.commit();
         }
      }

      sess.commit();

      conn.start();

      spoilerThread.start();

      assertTrue(flagAlign.await(10, TimeUnit.SECONDS));


      MessageConsumer consumer = sess.createConsumer(queue);

      // We won't receive the whole thing here.. we just want to validate if message will arrive or not...
      // this test is not meant to validate transactionality during Failover as that would require XA and recovery
      for (int i = 0; i < 90; i++)
      {
         TextMessage message = null;

         int retryNrs = 0;
         do
         {
            retryNrs++;
            try
            {
               message = (TextMessage) consumer.receive(5000);
               assertNotNull(message);
               break;
            }
            catch (JMSException e)
            {
               new Exception("Exception on receive message", e).printStackTrace();
            }
         } while (retryNrs < 10);

         assertNotNull(message);

         try
         {
            sess.commit();
         }
         catch (Exception e)
         {
            new Exception("Exception during commit", e);
            sess.rollback();
         }

      }


      conn.close();


      spoilerThread.join();


   }


   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   @Before
   public void setUp() throws Exception
   {
      super.setUp();
      startServers();
   }

   /**
    * @throws Exception
    */
   protected void startServers() throws Exception
   {
      final boolean sharedStore = true;
      NodeManager nodeManager = new InVMNodeManager(!sharedStore);
      backuptc = new TransportConfiguration(INVM_CONNECTOR_FACTORY, backupParams);
      livetc = new TransportConfiguration(INVM_CONNECTOR_FACTORY);

      liveAcceptortc = new TransportConfiguration(INVM_ACCEPTOR_FACTORY);

      backupAcceptortc = new TransportConfiguration(INVM_ACCEPTOR_FACTORY, backupParams);

      backupParams.put(TransportConstants.SERVER_ID_PROP_NAME, 1);

      backupConf = createBasicConfig(0)
         .addAcceptorConfiguration(backupAcceptortc)
         .addConnectorConfiguration(livetc.getName(), livetc)
         .addConnectorConfiguration(backuptc.getName(), backuptc)
         .setSecurityEnabled(false)
         .setJournalType(getDefaultJournalType())
         .addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY, backupParams))
         .setBindingsDirectory(getBindingsDir())
         .setJournalMinFiles(2)
         .setJournalDirectory(getJournalDir())
         .setPagingDirectory(getPageDir())
         .setLargeMessagesDirectory(getLargeMessagesDir())
         .setPersistenceEnabled(true)
         .setHAPolicyConfiguration(sharedStore ? new SharedStoreSlavePolicyConfiguration() : new ReplicaPolicyConfiguration())
         .addClusterConfiguration(basicClusterConnectionConfig(backuptc.getName(), livetc.getName()));

      backupService = new InVMNodeManagerServer(backupConf, nodeManager);

      backupJMSService = new JMSServerManagerImpl(backupService);

      backupJMSService.setContext(ctx2);

      backupJMSService.getHornetQServer().setIdentity("JMSBackup");
      log.info("Starting backup");
      backupJMSService.start();

      liveConf = createBasicConfig(0)
         .setJournalDirectory(getJournalDir())
         .setBindingsDirectory(getBindingsDir())
         .setSecurityEnabled(false)
         .addAcceptorConfiguration(liveAcceptortc)
         .setJournalType(getDefaultJournalType())
         .setBindingsDirectory(getBindingsDir())
         .setJournalMinFiles(2)
         .setJournalDirectory(getJournalDir())
         .setPagingDirectory(getPageDir())
         .setLargeMessagesDirectory(getLargeMessagesDir())
         .addConnectorConfiguration(livetc.getName(), livetc)
         .setPersistenceEnabled(true)
         .setHAPolicyConfiguration(sharedStore ? new SharedStoreMasterPolicyConfiguration() : new ReplicatedPolicyConfiguration())
         .addClusterConfiguration(basicClusterConnectionConfig(livetc.getName()));

      liveService = new InVMNodeManagerServer(liveConf, nodeManager);

      liveJMSService = new JMSServerManagerImpl(liveService);

      liveJMSService.setContext(ctx1);

      liveJMSService.getHornetQServer().setIdentity("JMSLive");
      log.info("Starting life");

      liveJMSService.start();

      JMSUtil.waitForServer(backupService);
   }

   @Override
   @After
   public void tearDown() throws Exception
   {
      backupJMSService.stop();

      liveJMSService.stop();

      Assert.assertEquals(0, InVMRegistry.instance.size());

      liveService = null;

      liveJMSService = null;

      backupJMSService = null;

      ctx1 = null;

      ctx2 = null;

      backupService = null;

      backupParams = null;

      super.tearDown();
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   private static class MyExceptionListener implements ExceptionListener
   {
      volatile JMSException e;

      public void onException(final JMSException e)
      {
         this.e = e;
      }
   }
}
