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

import org.apache.activemq6.api.core.client.ClientConsumer;
import org.apache.activemq6.api.core.client.ClientMessage;
import org.apache.activemq6.api.core.client.ClientProducer;
import org.apache.activemq6.api.core.client.ClientSession;
import org.apache.activemq6.api.core.client.ClientSessionFactory;
import org.apache.activemq6.api.core.client.ServerLocator;
import org.apache.activemq6.core.server.HornetQServer;
import org.apache.activemq6.tests.util.ServiceTestBase;
import org.junit.Assert;
import org.junit.Test;

/**
 * A BlockingSendTest
 *
 * @author <mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 */
public class BlockingSendTest extends ServiceTestBase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   @Test
   public void testSinglePersistentBlockingNonSync() throws Exception
   {
      HornetQServer server = createServer(true);
      ClientSession session = null;
      ClientSessionFactory factory = null;

      ServerLocator locator = null;

      server.getConfiguration().setJournalSyncNonTransactional(false);
      server.getConfiguration().setJournalBufferTimeout_AIO(15000);

      server.start();

      System.out.println("sync = " + server.getConfiguration().isJournalSyncNonTransactional());
      locator = createFactory(false);
      locator.setBlockOnDurableSend(true);
      factory = createSessionFactory(locator);

      session = factory.createSession();

      session.createQueue("address", "queue");

      ClientProducer prod = session.createProducer("address");

      ClientMessage message = session.createMessage(true);

      prod.send(message);

      ClientConsumer consumer = session.createConsumer("queue");

      session.start();

      ClientMessage msg = consumer.receive(5000);

      Assert.assertNotNull(msg);

      msg.acknowledge();
   }
}