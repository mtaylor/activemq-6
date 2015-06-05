package org.apache.activemq.artemis.core.protocol.mqtt;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.BindingQueryResult;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerMessage;

import java.util.Iterator;


public class MQTTRetainMessageManager
{
   private MQTTSession session;

   public MQTTRetainMessageManager(MQTTSession session)
   {
      this.session = session;
   }

   /** FIXME
   *  Retained messages should be handled in the core API.  There is currently no support for retained messages
   *  at the time of writing.  Instead we handle retained messages here.  This method will create a new queue for
   *  every address that is used to store retained messages.  THere should only ever be one message in the retained
   *  message queue.  When a new subscription is created the queue should be browsed and the message copied onto
   *  the subscription queue for the consumer.  When a new retained message is received the message will be sent to
   *  the retained queue and the previous retain message consumed to remove it from the queue. */
   void handleRetainedMessage(ServerMessage message, String address, boolean reset) throws Exception
   {
      SimpleString retainAddress = new SimpleString(MQTTUtil.convertMQTTAddressFilterToCoreRetain(address));

      if (!session.getServerSession().executeQueueQuery(retainAddress).isExists())
      {
         session.getServerSession().createQueue(retainAddress, retainAddress, null, false, true);
      }
      Queue queue = session.getServer().locateQueue(retainAddress);

      // Set the address of this message to the retained queue.
      message.setAddress(retainAddress);

      Iterator<MessageReference> iterator = queue.iterator();
      synchronized (iterator)
      {
         if (iterator.hasNext())
         {
            Long messageId = iterator.next().getMessage().getMessageID();
            queue.deleteReference(messageId);
         }

         if (!reset)
         {
            session.getServerSession().send(message.copy(), true);
         }
      }
   }

   void addRetainedMessagesToQueue(SimpleString queueName, String address) throws Exception
   {
      // Queue to add the retained messages to
      Queue queue = session.getServer().locateQueue(queueName);

      // The address filter that matches all retained message queues.
      String retainAddress = MQTTUtil.convertMQTTAddressFilterToCoreRetain(address);
      BindingQueryResult bindingQueryResult = session.getServerSession().executeBindingQuery(new SimpleString(retainAddress));

      // Iterate over all matching retain queues and add the head message to the original queue.
      for (SimpleString retainedQueueName : bindingQueryResult.getQueueNames())
      {
         Queue retainedQueue = session.getServer().locateQueue(retainedQueueName);
         synchronized (this)
         {
            Iterator<MessageReference> i = retainedQueue.iterator();
            if (i.hasNext())
            {
               ServerMessage message = i.next().getMessage().copy(session.getConnection().getServer().getStorageManager().generateID());
               queue.addTail(message.createReference(queue), true);
            }
         }
      }
   }
}
