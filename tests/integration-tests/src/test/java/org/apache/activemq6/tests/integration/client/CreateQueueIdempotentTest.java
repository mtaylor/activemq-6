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
package org.apache.activemq6.tests.integration.client;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq6.api.core.HornetQException;
import org.apache.activemq6.api.core.HornetQQueueExistsException;
import org.apache.activemq6.api.core.SimpleString;
import org.apache.activemq6.api.core.TransportConfiguration;
import org.apache.activemq6.api.core.client.ClientSession;
import org.apache.activemq6.api.core.client.ClientSessionFactory;
import org.apache.activemq6.api.core.client.ServerLocator;
import org.apache.activemq6.core.config.Configuration;
import org.apache.activemq6.core.server.HornetQServer;
import org.apache.activemq6.core.server.HornetQServers;
import org.apache.activemq6.tests.util.ServiceTestBase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CreateQueueIdempotentTest extends ServiceTestBase
{

   private HornetQServer server;

   @Override
   @Before
   public void setUp() throws Exception
   {
      super.setUp();

      Configuration conf = createDefaultConfig()
         .setSecurityEnabled(false)
         .addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY));

      server = addServer(HornetQServers.newHornetQServer(conf, true));
      server.start();
   }

   @Test
   public void testSequentialCreateQueueIdempotency() throws Exception
   {
      final SimpleString QUEUE = new SimpleString("SequentialCreateQueueIdempotency");

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QUEUE, QUEUE, null, true);

      try
      {
         session.createQueue(QUEUE, QUEUE, null, true);
         fail("Expected exception, queue already exists");
      }
      catch (HornetQQueueExistsException qee)
      {
         //ok
      }
      catch (HornetQException e)
      {
         fail("Invalid Exception type:" + e.getType());
      }
   }

   @Test
   public void testConcurrentCreateQueueIdempotency() throws Exception
   {
      final String QUEUE = "ConcurrentCreateQueueIdempotency";
      AtomicInteger queuesCreated = new AtomicInteger(0);
      AtomicInteger failedAttempts = new AtomicInteger(0);

      final int NUM_THREADS = 5;

      QueueCreator[] queueCreators = new QueueCreator[NUM_THREADS];


      for (int i = 0; i < NUM_THREADS; i++)
      {
         QueueCreator queueCreator = new QueueCreator(QUEUE, queuesCreated, failedAttempts);
         queueCreators[i] = queueCreator;
      }

      for (int i = 0; i < NUM_THREADS; i++)
      {
         queueCreators[i].start();
      }

      for (int i = 0; i < NUM_THREADS; i++)
      {
         queueCreators[i].join();
      }

      server.stop();

      // re-starting the server appears to be an unreliable guide
      server.start();

      Assert.assertEquals(1, queuesCreated.intValue());
      Assert.assertEquals(NUM_THREADS - 1, failedAttempts.intValue());
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   class QueueCreator extends Thread
   {
      private String queueName = null;
      private AtomicInteger queuesCreated = null;
      private AtomicInteger failedAttempts = null;


      QueueCreator(String queueName, AtomicInteger queuesCreated, AtomicInteger failedAttempts)
      {
         this.queueName = queueName;
         this.queuesCreated = queuesCreated;
         this.failedAttempts = failedAttempts;
      }

      @Override
      public void run()
      {
         ServerLocator locator = null;
         ClientSession session = null;

         try
         {
            locator = createInVMNonHALocator();
            ClientSessionFactory sf = createSessionFactory(locator);
            session = sf.createSession(false, true, true);
            final SimpleString QUEUE = new SimpleString(queueName);
            session.createQueue(QUEUE, QUEUE, null, true);
            queuesCreated.incrementAndGet();
         }
         catch (HornetQQueueExistsException qne)
         {
            failedAttempts.incrementAndGet();
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }
         finally
         {
            if (locator != null)
            {
               locator.close();
            }
            if (session != null)
            {
               try
               {
                  session.close();
               }
               catch (HornetQException e)
               {
                  e.printStackTrace();
               }
            }
         }
      }
   }
}
