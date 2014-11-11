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
package org.apache.activemq6.tests.integration.scheduling;
import org.junit.Before;

import org.junit.Test;

import org.junit.Assert;

import org.apache.activemq6.api.core.client.ClientConsumer;
import org.apache.activemq6.api.core.client.ClientMessage;
import org.apache.activemq6.api.core.client.ClientProducer;
import org.apache.activemq6.api.core.client.ClientSession;
import org.apache.activemq6.api.core.client.ClientSessionFactory;
import org.apache.activemq6.api.core.client.ServerLocator;
import org.apache.activemq6.core.config.Configuration;
import org.apache.activemq6.core.server.HornetQServer;
import org.apache.activemq6.core.settings.impl.AddressSettings;
import org.apache.activemq6.jms.client.HornetQTextMessage;
import org.apache.activemq6.tests.integration.IntegrationTestLogger;
import org.apache.activemq6.tests.util.ServiceTestBase;
import org.apache.activemq6.tests.util.UnitTestCase;

/**
 * @author <a href="jmesnil@redhat.com">Jeff Mesnil</a>
 */
public class DelayedMessageTest extends ServiceTestBase
{
   private static final IntegrationTestLogger log = IntegrationTestLogger.LOGGER;

   private Configuration configuration;

   private HornetQServer server;

   private static final long DELAY = 3000;

   private final String qName = "DelayedMessageTestQueue";

   private ServerLocator locator;

   @Override
   @Before
   public void setUp() throws Exception
   {
      super.setUp();
      initServer();
   }

   /**
    * @throws Exception
    */
   protected void initServer() throws Exception
   {
      configuration = createDefaultConfig()
         .setSecurityEnabled(false)
         .setJournalMinFiles(2);
      server = createServer(true, configuration);
      server.start();

      AddressSettings qs = server.getAddressSettingsRepository().getMatch("*");
      AddressSettings newSets = new AddressSettings();
      newSets.setRedeliveryDelay(DelayedMessageTest.DELAY);
      newSets.merge(qs);
      server.getAddressSettingsRepository().addMatch(qName, newSets);
      locator = createInVMNonHALocator();
   }

   @Test
   public void testDelayedRedeliveryDefaultOnClose() throws Exception
   {
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, false, false);

      session.createQueue(qName, qName, null, true);
      session.close();

      ClientSession session1 = sessionFactory.createSession(false, true, true);
      ClientProducer producer = session1.createProducer(qName);

      final int NUM_MESSAGES = 5;

      UnitTestCase.forceGC();

      for (int i = 0; i < NUM_MESSAGES; i++)
      {
         ClientMessage tm = createDurableMessage(session1, "message" + i);
         producer.send(tm);
      }

      session1.close();

      ClientSession session2 = sessionFactory.createSession(false, false, false);

      ClientConsumer consumer2 = session2.createConsumer(qName);
      session2.start();

      for (int i = 0; i < NUM_MESSAGES; i++)
      {
         ClientMessage tm = consumer2.receive(500);

         tm.acknowledge();

         Assert.assertNotNull(tm);

         Assert.assertEquals("message" + i, tm.getBodyBuffer().readString());
      }

      // Now close the session
      // This should cancel back to the queue with a delayed redelivery

      long now = System.currentTimeMillis();

      session2.close();

      ClientSession session3 = sessionFactory.createSession(false, false, false);

      ClientConsumer consumer3 = session3.createConsumer(qName);
      session3.start();
      for (int i = 0; i < NUM_MESSAGES; i++)
      {
         ClientMessage tm = consumer3.receive(DelayedMessageTest.DELAY + 1000);

         Assert.assertNotNull(tm);

         long time = System.currentTimeMillis();

         log.info("delay " + (time - now));

         Assert.assertTrue(time - now >= DelayedMessageTest.DELAY);

         // Hudson can introduce a large degree of indeterminism
         Assert.assertTrue(time - now + ">" + (DelayedMessageTest.DELAY + 1000),
                           time - now < DelayedMessageTest.DELAY + 1000);
      }

      session3.commit();
      session3.close();


   }

   @Test
   public void testDelayedRedeliveryDefaultOnRollback() throws Exception
   {
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, false, false);

      session.createQueue(qName, qName, null, true);
      session.close();

      ClientSession session1 = sessionFactory.createSession(false, true, true);
      ClientProducer producer = session1.createProducer(qName);

      final int NUM_MESSAGES = 5;

      for (int i = 0; i < NUM_MESSAGES; i++)
      {
         ClientMessage tm = createDurableMessage(session1, "message" + i);
         producer.send(tm);
      }
      session1.close();

      ClientSession session2 = sessionFactory.createSession(false, false, false);
      ClientConsumer consumer2 = session2.createConsumer(qName);

      session2.start();

      for (int i = 0; i < NUM_MESSAGES; i++)
      {
         ClientMessage tm = consumer2.receive(500);
         Assert.assertNotNull(tm);
         Assert.assertEquals("message" + i, tm.getBodyBuffer().readString());
      }

      // Now rollback
      long now = System.currentTimeMillis();

      session2.rollback();

      // This should redeliver with a delayed redelivery

      for (int i = 0; i < NUM_MESSAGES; i++)
      {
         ClientMessage tm = consumer2.receive(DelayedMessageTest.DELAY + 1000);
         Assert.assertNotNull(tm);

         long time = System.currentTimeMillis();

         Assert.assertTrue(time - now >= DelayedMessageTest.DELAY);

         // Hudson can introduce a large degree of indeterminism
         Assert.assertTrue(time - now + ">" + (DelayedMessageTest.DELAY + 1000),
                           time - now < DelayedMessageTest.DELAY + 1000);
      }

      session2.commit();
      session2.close();


   }

   @Test
   public void testDelayedRedeliveryWithStart() throws Exception
   {
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      ClientSession session = sessionFactory.createSession(false, false, false);

      session.createQueue(qName, qName, null, true);
      session.close();

      ClientSession session1 = sessionFactory.createSession(false, true, true);
      ClientProducer producer = session1.createProducer(qName);

      final int NUM_MESSAGES = 1;

      for (int i = 0; i < NUM_MESSAGES; i++)
      {
         ClientMessage tm = createDurableMessage(session1, "message" + i);
         producer.send(tm);
      }
      session1.close();

      ClientSession session2 = sessionFactory.createSession(false, false, false);
      ClientConsumer consumer2 = session2.createConsumer(qName);

      session2.start();

      for (int i = 0; i < NUM_MESSAGES; i++)
      {
         ClientMessage tm = consumer2.receive(500);
         Assert.assertNotNull(tm);
         Assert.assertEquals("message" + i, tm.getBodyBuffer().readString());
      }

      // Now rollback
      long now = System.currentTimeMillis();


      session2.rollback();

      session2.close();

      sessionFactory.close();

      locator.close();

      server.stop();

      initServer();

      sessionFactory = createSessionFactory(locator);

      session2 = sessionFactory.createSession(false, false, false);

      consumer2 = session2.createConsumer(qName);

      Thread.sleep(3000);

      session2.start();

      // This should redeliver with a delayed redelivery

      for (int i = 0; i < NUM_MESSAGES; i++)
      {
         ClientMessage tm = consumer2.receive(DelayedMessageTest.DELAY + 1000);
         Assert.assertNotNull(tm);

         long time = System.currentTimeMillis();

         Assert.assertTrue(time - now >= DelayedMessageTest.DELAY);

         // Hudson can introduce a large degree of indeterminism
      }

      session2.commit();
      session2.close();


   }

   // Private -------------------------------------------------------

   private ClientMessage createDurableMessage(final ClientSession session, final String body)
   {
      ClientMessage message = session.createMessage(HornetQTextMessage.TYPE,
                                                    true,
                                                    0,
                                                    System.currentTimeMillis(),
                                                    (byte)1);
      message.getBodyBuffer().writeString(body);
      return message;
   }
}
