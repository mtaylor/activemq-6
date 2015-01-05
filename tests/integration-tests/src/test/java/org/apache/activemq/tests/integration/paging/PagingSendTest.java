/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.tests.integration.paging;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.api.core.ActiveMQException;
import org.apache.activemq.api.core.Message;
import org.apache.activemq.api.core.SimpleString;
import org.apache.activemq.api.core.client.ClientConsumer;
import org.apache.activemq.api.core.client.ClientMessage;
import org.apache.activemq.api.core.client.ClientProducer;
import org.apache.activemq.api.core.client.ClientSession;
import org.apache.activemq.api.core.client.ClientSessionFactory;
import org.apache.activemq.api.core.client.ServerLocator;
import org.apache.activemq.core.config.Configuration;
import org.apache.activemq.core.config.impl.ConfigurationImpl;
import org.apache.activemq.core.paging.cursor.PageSubscription;
import org.apache.activemq.core.paging.cursor.PagedReference;
import org.apache.activemq.core.postoffice.Binding;
import org.apache.activemq.core.postoffice.Bindings;
import org.apache.activemq.core.server.ActiveMQServer;
import org.apache.activemq.core.server.impl.QueueImpl;
import org.apache.activemq.core.settings.impl.AddressSettings;
import org.apache.activemq.tests.util.ServiceTestBase;
import org.apache.activemq.utils.LinkedListIterator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * A SendTest
 *
 * @author <mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 */
public class PagingSendTest extends ServiceTestBase
{
   public static final SimpleString ADDRESS = new SimpleString("SimpleAddress");

   private ServerLocator locator;

   private ActiveMQServer server;

   protected boolean isNetty()
   {
      return false;
   }

   @Override
   @Before
   public void setUp() throws Exception
   {
      super.setUp();
      Configuration config = new ConfigurationImpl();
      server = newActiveMQServer();

      server.start();
      waitForServer(server);
      locator = createFactory(isNetty());
   }

   private ActiveMQServer newActiveMQServer() throws Exception
   {
      ActiveMQServer server = createServer(true, isNetty());

      AddressSettings defaultSetting = new AddressSettings();
      defaultSetting.setPageSizeBytes(10 * 1024);
      defaultSetting.setMaxSizeBytes(20 * 1024);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   @Test
   public void testSameMessageOverAndOverBlocking() throws Exception
   {
      dotestSameMessageOverAndOver(true);
   }

   @Test
   public void testSameMessageOverAndOverNonBlocking() throws Exception
   {
      dotestSameMessageOverAndOver(false);
   }

   public void dotestSameMessageOverAndOver(final boolean blocking) throws Exception
   {
      // Making it synchronous, just because we want to stop sending messages as soon as the
      // page-store becomes in
      // page mode
      // and we could only guarantee that by setting it to synchronous
      locator.setBlockOnNonDurableSend(blocking);
      locator.setBlockOnDurableSend(blocking);
      locator.setBlockOnAcknowledge(blocking);

      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

      session.createQueue(PagingSendTest.ADDRESS, PagingSendTest.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(PagingSendTest.ADDRESS);

      ClientMessage message = null;

      message = session.createMessage(true);
      message.getBodyBuffer().writeBytes(new byte[1024]);

      for (int i = 0; i < 200; i++)
      {
         producer.send(message);
      }

      session.close();

      session = sf.createSession(null, null, false, true, true, false, 0);

      ClientConsumer consumer = session.createConsumer(PagingSendTest.ADDRESS);

      session.start();

      for (int i = 0; i < 200; i++)
      {
         ClientMessage message2 = consumer.receive(10000);

         Assert.assertNotNull(message2);

         if (i == 100)
         {
            session.commit();
         }

         message2.acknowledge();
      }

      consumer.close();

      session.close();
   }

   @Test
   public void testOrderOverTX() throws Exception
   {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession sessionConsumer = sf.createSession(true, true, 0);

      sessionConsumer.createQueue(PagingSendTest.ADDRESS, PagingSendTest.ADDRESS, null, true);

      final ClientSession sessionProducer = sf.createSession(false, false);
      final ClientProducer producer = sessionProducer.createProducer(PagingSendTest.ADDRESS);

      final AtomicInteger errors = new AtomicInteger(0);

      final int TOTAL_MESSAGES = 1000;

      // Consumer will be ready after we have commits
      final CountDownLatch ready = new CountDownLatch(1);

      Thread tProducer = new Thread()
      {
         @Override
         public void run()
         {
            try
            {
               int commit = 0;
               for (int i = 0; i < TOTAL_MESSAGES; i++)
               {
                  ClientMessage msg = sessionProducer.createMessage(true);
                  msg.getBodyBuffer().writeBytes(new byte[1024]);
                  msg.putIntProperty("count", i);
                  producer.send(msg);

                  if (i % 100 == 0 && i > 0)
                  {
                     sessionProducer.commit();
                     if (commit++ > 2)
                     {
                        ready.countDown();
                     }
                  }
               }

               sessionProducer.commit();

            }
            catch (Exception e)
            {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         }
      };

      ClientConsumer consumer = sessionConsumer.createConsumer(PagingSendTest.ADDRESS);

      sessionConsumer.start();

      tProducer.start();

      assertTrue(ready.await(10, TimeUnit.SECONDS));

      for (int i = 0; i < TOTAL_MESSAGES; i++)
      {
         ClientMessage msg = consumer.receive(10000);

         Assert.assertNotNull(msg);

         assertEquals(i, msg.getIntProperty("count").intValue());

         msg.acknowledge();
      }

      tProducer.join();

      sessionConsumer.close();

      sessionProducer.close();

      assertEquals(0, errors.get());
   }

   @Test
   public void testPagingDoesNotDuplicateBatchMessages() throws Exception
   {
      int batchSize = 20;

      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, false);

      // Create a queue
      SimpleString queueAddr = new SimpleString("testQueue");
      session.createQueue(queueAddr, queueAddr, null, true);

      // Set up paging on the queue address
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setPageSizeBytes(10 * 1024);
      /** This actually causes the address to start paging messages after 10 x messages with 1024 payload is sent.
      Presumably due to additional meta-data, message headers etc... **/
      addressSettings.setMaxSizeBytes(16 * 1024);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);

      sendMessageBatch(batchSize, session, queueAddr);
      checkBatchMessagesAreNotPagedTwice(findQueue(queueAddr), new ArrayList<String>());
   }

   @Test
   public void testPagingDoesNotDuplicateBatchMessagesAfterPagingStarted() throws Exception
   {
      int batchSize = 20;

      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, false);

      // Create a queue
      SimpleString queueAddr = new SimpleString("testQueue");
      session.createQueue(queueAddr, queueAddr, null, true);

      // Set up paging on the queue address
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setPageSizeBytes(10 * 1024);
      /** This actually causes the address to start paging messages after 10 x messages with 1024 payload is sent.
       Presumably due to additional meta-data, message headers etc... **/
      addressSettings.setMaxSizeBytes(16 * 1024);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);

      List<String> ids = new ArrayList<String>();

      // ensure the server is paging
      while(!server.getPagingManager().getPageStore(queueAddr).isPaging())
      {
         ids.addAll(sendMessageBatch(batchSize, session, queueAddr));
      }

      // Send 20 messages after the server has started paging.  Ignoring any duplicates that may have happened before this point.
      sendMessageBatch(batchSize, session, queueAddr);
      checkBatchMessagesAreNotPagedTwice(findQueue(queueAddr), ids);
   }

   public List<String> sendMessageBatch(int batchSize, ClientSession session, SimpleString queueAddr) throws ActiveMQException
   {
      List<String> messageIds = new ArrayList<String>();
      ClientProducer producer = session.createProducer(queueAddr);
      for (int i = 0; i < batchSize; i++)
      {
         Message message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(new byte[1024]);
         String id =  UUID.randomUUID().toString();
         message.putStringProperty("id", id);
         messageIds.add(id);
         producer.send(message);
      }
      session.commit();

      return messageIds;
   }

   /**
    * checks that there are no message duplicates in the page.  Any IDs found in the ignoreIds field will not be tested
    * this allows us to test only those messages that have been sent after the address has started paging (ignoring any
    * duplicates that may have happened before this point).
    */
   public void checkBatchMessagesAreNotPagedTwice(QueueImpl queue, List<String> ignoreIds) throws Exception
   {
      // Get the PageSubscription iterator
      Field field = QueueImpl.class.getDeclaredField("pageSubscription");
      field.setAccessible(true);
      PageSubscription pageSubscription = (PageSubscription) field.get(queue);
      LinkedListIterator<PagedReference> pageIterator = pageSubscription.iterator();

      // TODO remove this line:  The list is used here so that all the paged message IDs can be inspected for debug.
      List<String> messageOrderList = new ArrayList<String>();

      Set<String> messageOrderSet = new HashSet<String>();

      int duplicates = 0;
      while (pageIterator.hasNext())
      {
         PagedReference reference = pageIterator.next();
         String id = reference.getPagedMessage().getMessage().getStringProperty("id");

         // TODO remove this line:  The list is used here so that all the paged message IDs can be inspected for debug.
         messageOrderList.add(id);

         // Ignore IDs that were previously sent (used to test only those messages sent after paging is enabled).
         if (!ignoreIds.contains(id))
         {
            // If add(id) returns true it means that this id was already added to this set.  Hence a duplicate is found.
            if (!messageOrderSet.add(id))
            {
               duplicates++;
            }
         }
      }
      assertTrue(duplicates == 0);
   }

   private QueueImpl findQueue(SimpleString queueAddr) throws Exception
   {
      QueueImpl testQueue = null;
      Bindings bindings = server.getPostOffice().getBindingsForAddress(queueAddr);
      for (Binding b : bindings.getBindings())
      {
         if (b.getBindable() instanceof QueueImpl)
         {
            testQueue = (QueueImpl) b.getBindable();
         }
      }
      return testQueue;
   }
}
