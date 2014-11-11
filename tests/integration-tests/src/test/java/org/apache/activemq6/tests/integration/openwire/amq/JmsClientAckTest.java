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
package org.apache.activemq6.tests.integration.openwire.amq;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq6.tests.integration.openwire.BasicOpenWireTest;
import org.junit.Test;

/**
 * adapted from: org.apache.activemq.JmsClientAckTest
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 *
 */
public class JmsClientAckTest extends BasicOpenWireTest
{
   /**
    * Tests if acknowledged messages are being consumed.
    *
    * @throws JMSException
    */
   @Test
   public void testAckedMessageAreConsumed() throws JMSException
   {
      connection.start();
      Session session = connection.createSession(false,
            Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue(getQueueName());
      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createTextMessage("Hello"));

      // Consume the message...
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      msg.acknowledge();

      // Reset the session.
      session.close();
      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receive(1000);
      assertNull(msg);

      session.close();
   }

   /**
    * Tests if acknowledged messages are being consumed.
    *
    * @throws JMSException
    */
   @Test
   public void testLastMessageAcked() throws JMSException
   {
      connection.start();
      Session session = connection.createSession(false,
            Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue(getQueueName());
      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createTextMessage("Hello"));
      producer.send(session.createTextMessage("Hello2"));
      producer.send(session.createTextMessage("Hello3"));

      // Consume the message...
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      msg = consumer.receive(1000);
      assertNotNull(msg);
      msg = consumer.receive(1000);
      assertNotNull(msg);
      msg.acknowledge();

      // Reset the session.
      session.close();
      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receive(1000);
      assertNull(msg);

      session.close();
   }

   /**
    * Tests if unacknowledged messages are being re-delivered when the consumer connects again.
    *
    * @throws JMSException
    */
   @Test
   public void testUnAckedMessageAreNotConsumedOnSessionClose() throws JMSException
   {
      connection.start();
      Session session = connection.createSession(false,
            Session.CLIENT_ACKNOWLEDGE);
      Queue queue = session.createQueue(getQueueName());
      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createTextMessage("Hello"));

      // Consume the message...
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);
      // Don't ack the message.

      // Reset the session. This should cause the unacknowledged message to be
      // re-delivered.
      session.close();
      session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receive(2000);
      assertNotNull(msg);
      msg.acknowledge();

      session.close();
   }

   protected String getQueueName()
   {
      return queueName;
   }

}