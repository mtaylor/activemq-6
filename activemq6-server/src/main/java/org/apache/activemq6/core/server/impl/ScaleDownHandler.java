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
package org.apache.activemq6.core.server.impl;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq6.api.core.Message;
import org.apache.activemq6.api.core.Pair;
import org.apache.activemq6.api.core.SimpleString;
import org.apache.activemq6.api.core.client.ClientMessage;
import org.apache.activemq6.api.core.client.ClientProducer;
import org.apache.activemq6.api.core.client.ClientRequestor;
import org.apache.activemq6.api.core.client.ClientSession;
import org.apache.activemq6.api.core.client.ClientSessionFactory;
import org.apache.activemq6.api.core.management.ManagementHelper;
import org.apache.activemq6.api.core.management.ResourceNames;
import org.apache.activemq6.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq6.core.message.impl.MessageImpl;
import org.apache.activemq6.core.paging.PagingManager;
import org.apache.activemq6.core.paging.PagingStore;
import org.apache.activemq6.core.paging.cursor.PageSubscription;
import org.apache.activemq6.core.paging.cursor.PagedReference;
import org.apache.activemq6.core.postoffice.Binding;
import org.apache.activemq6.core.postoffice.PostOffice;
import org.apache.activemq6.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq6.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq6.core.server.HornetQServerLogger;
import org.apache.activemq6.core.server.MessageReference;
import org.apache.activemq6.core.server.NodeManager;
import org.apache.activemq6.core.server.Queue;
import org.apache.activemq6.core.server.ServerMessage;
import org.apache.activemq6.core.server.cluster.ClusterControl;
import org.apache.activemq6.core.server.cluster.ClusterController;
import org.apache.activemq6.core.transaction.ResourceManager;
import org.apache.activemq6.core.transaction.Transaction;
import org.apache.activemq6.core.transaction.TransactionOperation;
import org.apache.activemq6.utils.LinkedListIterator;

public class ScaleDownHandler
{
   final PagingManager pagingManager;
   final PostOffice postOffice;
   private NodeManager nodeManager;
   private final ClusterController clusterController;
   private String targetNodeId;

   public ScaleDownHandler(PagingManager pagingManager, PostOffice postOffice, NodeManager nodeManager, ClusterController clusterController)
   {
      this.pagingManager = pagingManager;
      this.postOffice = postOffice;
      this.nodeManager = nodeManager;
      this.clusterController = clusterController;
   }

   public long scaleDown(ClientSessionFactory sessionFactory,
                         ResourceManager resourceManager,
                         Map<SimpleString,
                         List<Pair<byte[], Long>>> duplicateIDMap,
                         SimpleString managementAddress,
                         SimpleString targetNodeId) throws Exception
   {
      ClusterControl clusterControl = clusterController.connectToNodeInCluster((ClientSessionFactoryInternal) sessionFactory);
      clusterControl.authorize();
      long num = scaleDownMessages(sessionFactory, targetNodeId);
      HornetQServerLogger.LOGGER.info("Scaled down " + num + " messages total.");
      scaleDownTransactions(sessionFactory, resourceManager);
      scaleDownDuplicateIDs(duplicateIDMap, sessionFactory, managementAddress);
      clusterControl.announceScaleDown(new SimpleString(this.targetNodeId), nodeManager.getNodeId());
      return num;
   }

   private long scaleDownMessages(ClientSessionFactory sessionFactory, SimpleString nodeId) throws Exception
   {
      long messageCount = 0;
      targetNodeId = nodeId != null ? nodeId.toString() : getTargetNodeId(sessionFactory);

      ClientSession session = sessionFactory.createSession(false, true, true);
      Map<String, Long> queueIDs = new HashMap<>();
      ClientProducer producer = session.createProducer();

      List<SimpleString> addresses = new ArrayList<>();
      for (Map.Entry<SimpleString, Binding> entry : postOffice.getAllBindings().entrySet())
      {
         if (entry.getValue() instanceof LocalQueueBinding)
         {
            SimpleString address = entry.getValue().getAddress();

            // There is a special case involving store-and-forward queues used for clustering.
            // If this queue is supposed to forward messages to the server that I'm scaling down to I need to handle these messages differently.
            boolean storeAndForward = false;
            if (address.toString().startsWith("sf."))
            {
               // these get special treatment later
               storeAndForward = true;
            }

            // this means we haven't inspected this address before
            if (!addresses.contains(address))
            {
               addresses.add(address);

               PagingStore store = pagingManager.getPageStore(address);

               // compile a list of all the relevant queues and queue iterators for this address
               List<Queue> queues = new ArrayList<>();
               Map<SimpleString, LinkedListIterator<MessageReference>> queueIterators = new HashMap<>();
               for (Binding binding : postOffice.getBindingsForAddress(address).getBindings())
               {
                  if (binding instanceof LocalQueueBinding)
                  {
                     Queue queue = ((LocalQueueBinding) binding).getQueue();
                     //remove the scheduled messages and reset on the actual message ready for sending
                     //we may set the time multiple times on a message but it will always be the same.
                     //set the ref scheduled time to 0 so it is in the queue ready for resending
                     List<MessageReference> messageReferences = queue.cancelScheduledMessages();
                     for (MessageReference ref : messageReferences)
                     {
                        ref.getMessage().putLongProperty(MessageImpl.HDR_SCHEDULED_DELIVERY_TIME, ref.getScheduledDeliveryTime());
                        ref.setScheduledDeliveryTime(0);
                     }
                     queue.addHead(messageReferences);
                     queues.add(queue);
                     queueIterators.put(queue.getName(), queue.totalIterator());
                  }
               }

               // sort into descending order - order is based on the number of references in the queue
               Collections.sort(queues, new OrderQueueByNumberOfReferencesComparator());

               // loop through every queue on this address
               List<SimpleString> checkedQueues = new ArrayList<>();
               for (Queue bigLoopQueue : queues)
               {
                  checkedQueues.add(bigLoopQueue.getName());

                  LinkedListIterator<MessageReference> bigLoopMessageIterator = bigLoopQueue.totalIterator();
                  try
                  {
                     // loop through every message of this queue
                     while (bigLoopMessageIterator.hasNext())
                     {
                        MessageReference bigLoopRef = bigLoopMessageIterator.next();
                        Message message = bigLoopRef.getMessage().copy();

                        if (storeAndForward)
                        {
                           if (address.toString().endsWith(targetNodeId))
                           {
                              /* Here we are taking messages out of a store-and-forward queue and sending them to the corresponding
                               * address on the scale-down target server.  However, we have to take the existing _HQ_ROUTE_TOsf.*
                               * property and put its value into the _HQ_ROUTE_TO property so the message is routed properly.
                               */

                              byte[] oldRouteToIDs = null;

                              List<SimpleString> propertiesToRemove = new ArrayList<>();
                              message.removeProperty(MessageImpl.HDR_ROUTE_TO_IDS);
                              for (SimpleString propName : message.getPropertyNames())
                              {
                                 if (propName.startsWith(MessageImpl.HDR_ROUTE_TO_IDS))
                                 {
                                    if (propName.toString().endsWith(targetNodeId))
                                    {
                                       oldRouteToIDs = message.getBytesProperty(propName);
                                    }
                                    propertiesToRemove.add(propName);
                                 }
                              }

                              for (SimpleString propertyToRemove : propertiesToRemove)
                              {
                                 message.removeProperty(propertyToRemove);
                              }

                              message.putBytesProperty(MessageImpl.HDR_ROUTE_TO_IDS, oldRouteToIDs);
                           }
                           else
                           {
                              /* Here we are taking messages out of a store-and-forward queue and sending them to the corresponding
                               * store-and-forward address on the scale-down target server.  In this case we use a special property
                               * for the queue ID so that the scale-down target server can route it appropriately.
                               */
                              byte[] oldRouteToIDs = null;

                              List<SimpleString> propertiesToRemove = new ArrayList<>();
                              message.removeProperty(MessageImpl.HDR_ROUTE_TO_IDS);
                              for (SimpleString propName : message.getPropertyNames())
                              {
                                 if (propName.startsWith(MessageImpl.HDR_ROUTE_TO_IDS))
                                 {
                                    if (propName.toString().endsWith(address.toString().substring(address.toString().lastIndexOf("."))))
                                    {
                                       oldRouteToIDs = message.getBytesProperty(propName);
                                    }
                                    propertiesToRemove.add(propName);
                                 }
                              }

                              for (SimpleString propertyToRemove : propertiesToRemove)
                              {
                                 message.removeProperty(propertyToRemove);
                              }

                              message.putBytesProperty(MessageImpl.HDR_SCALEDOWN_TO_IDS, oldRouteToIDs);
                           }

                           HornetQServerLogger.LOGGER.debug("Scaling down message " + message + " from " + address + " to " + message.getAddress() + " on node " + targetNodeId);
                           producer.send(message.getAddress(), message);
                           messageCount++;
                           bigLoopQueue.deleteReference(message.getMessageID());
                        }
                        else
                        {
                           List<Queue> queuesWithMessage = new ArrayList<>();
                           queuesWithMessage.add(bigLoopQueue);
                           long messageId = message.getMessageID();

                           getQueuesWithMessage(store, queues, queueIterators, checkedQueues, bigLoopQueue, queuesWithMessage, bigLoopRef, messageId);

                           // get the ID for every queue that contains the message
                           ByteBuffer buffer = ByteBuffer.allocate(queuesWithMessage.size() * 8);
                           StringBuilder logMessage = new StringBuilder();
                           logMessage.append("Scaling down message ").append(messageId).append(" to ");
                           for (Queue queue : queuesWithMessage)
                           {
                              long queueID;
                              String queueName = queue.getName().toString();

                              if (queueIDs.containsKey(queueName))
                              {
                                 queueID = queueIDs.get(queueName);
                              }
                              else
                              {
                                 queueID = createQueueIfNecessaryAndGetID(session, queue, address);
                                 queueIDs.put(queueName, queueID);  // store it so we don't have to look it up every time
                              }

                              logMessage.append(queueName).append("(").append(queueID).append(")").append(", ");
                              buffer.putLong(queueID);
                           }

                           logMessage.delete(logMessage.length() - 2, logMessage.length());  // trim off the trailing comma and space
                           HornetQServerLogger.LOGGER.debug(logMessage.append(" on address ").append(address));

                           message.putBytesProperty(MessageImpl.HDR_ROUTE_TO_IDS, buffer.array());
                           //we need this incase we are sending back to the source server of the message, this basically
                           //acts like the bridge and ignores dup detection
                           if (message.containsProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID))
                           {
                              byte[] bytes = new byte[24];

                              ByteBuffer bb = ByteBuffer.wrap(bytes);
                              bb.put(nodeManager.getUUID().asBytes());
                              bb.putLong(messageId);

                              message.putBytesProperty(MessageImpl.HDR_BRIDGE_DUPLICATE_ID, bb.array());
                           }

                           producer.send(address, message);
                           messageCount++;

                           // delete the reference from all queues which contain it
                           bigLoopQueue.deleteReference(messageId);
                           for (Queue queue : queuesWithMessage)
                           {
                              queue.deleteReference(messageId);
                           }
                        }
                     }
                  }
                  finally
                  {
                     bigLoopMessageIterator.close();
                     queueIterators.get(bigLoopQueue.getName()).close();
                  }
               }
            }
         }
      }

      producer.close();
      session.close();

      return messageCount;
   }

   private String getTargetNodeId(ClientSessionFactory sessionFactory)
   {
      return sessionFactory.getServerLocator().getTopology().getMember(sessionFactory.getConnectorConfiguration()).getNodeId();
   }

   public void scaleDownTransactions(ClientSessionFactory sessionFactory, ResourceManager resourceManager) throws Exception
   {
      ClientSession session = sessionFactory.createSession(true, false, false);
      ClientSession queueCreateSession = sessionFactory.createSession(false, true, true);
      List<Xid> preparedTransactions = resourceManager.getPreparedTransactions();
      Map<String, Long> queueIDs = new HashMap<>();
      for (Xid xid : preparedTransactions)
      {
         HornetQServerLogger.LOGGER.debug("Scaling down transaction: " + xid);
         Transaction transaction = resourceManager.getTransaction(xid);
         session.start(xid, XAResource.TMNOFLAGS);
         List<TransactionOperation> allOperations = transaction.getAllOperations();
         Map<ServerMessage, Pair<List<Long>, List<Long>>> queuesToSendTo = new HashMap<>();
         for (TransactionOperation operation : allOperations)
         {
            if (operation instanceof PostOfficeImpl.AddOperation)
            {
               PostOfficeImpl.AddOperation addOperation = (PostOfficeImpl.AddOperation) operation;
               List<MessageReference> refs = addOperation.getRelatedMessageReferences();
               for (MessageReference ref : refs)
               {
                  ServerMessage message = ref.getMessage();
                  Queue queue = ref.getQueue();
                  long queueID;
                  String queueName = queue.getName().toString();

                  if (queueIDs.containsKey(queueName))
                  {
                     queueID = queueIDs.get(queueName);
                  }
                  else
                  {
                     queueID = createQueueIfNecessaryAndGetID(queueCreateSession, queue, message.getAddress());
                     queueIDs.put(queueName, queueID);  // store it so we don't have to look it up every time
                  }
                  Pair<List<Long>, List<Long>> queueIds = queuesToSendTo.get(message);
                  if (queueIds == null)
                  {
                     queueIds = new Pair<List<Long>, List<Long>>(new ArrayList<Long>(), new ArrayList<Long>());
                     queuesToSendTo.put(message, queueIds);
                  }
                  queueIds.getA().add(queueID);
               }
            }
            else if (operation instanceof RefsOperation)
            {
               RefsOperation refsOperation = (RefsOperation) operation;
               List<MessageReference> refs = refsOperation.getReferencesToAcknowledge();
               for (MessageReference ref : refs)
               {
                  ServerMessage message = ref.getMessage();
                  Queue queue = ref.getQueue();
                  long queueID;
                  String queueName = queue.getName().toString();

                  if (queueIDs.containsKey(queueName))
                  {
                     queueID = queueIDs.get(queueName);
                  }
                  else
                  {
                     queueID = createQueueIfNecessaryAndGetID(queueCreateSession, queue, message.getAddress());
                     queueIDs.put(queueName, queueID);  // store it so we don't have to look it up every time
                  }
                  Pair<List<Long>, List<Long>> queueIds = queuesToSendTo.get(message);
                  if (queueIds == null)
                  {
                     queueIds = new Pair<List<Long>, List<Long>>(new ArrayList<Long>(), new ArrayList<Long>());
                     queuesToSendTo.put(message, queueIds);
                  }
                  queueIds.getA().add(queueID);
                  queueIds.getB().add(queueID);
               }
            }
         }
         ClientProducer producer = session.createProducer();
         for (Map.Entry<ServerMessage, Pair<List<Long>, List<Long>>> entry : queuesToSendTo.entrySet())
         {
            List<Long> ids = entry.getValue().getA();
            ByteBuffer buffer = ByteBuffer.allocate(ids.size() * 8);
            for (Long id : ids)
            {
               buffer.putLong(id);
            }
            ServerMessage message = entry.getKey();
            message.putBytesProperty(MessageImpl.HDR_ROUTE_TO_IDS, buffer.array());
            ids = entry.getValue().getB();
            if (ids.size() > 0)
            {
               buffer = ByteBuffer.allocate(ids.size() * 8);
               for (Long id : ids)
               {
                  buffer.putLong(id);
               }
               message.putBytesProperty(MessageImpl.HDR_ROUTE_TO_ACK_IDS, buffer.array());
            }
            producer.send(message.getAddress(), message);
         }
         session.end(xid, XAResource.TMSUCCESS);
         session.prepare(xid);
      }
   }


   public void scaleDownDuplicateIDs(Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap, ClientSessionFactory sessionFactory, SimpleString managementAddress) throws Exception
   {
      ClientSession session = sessionFactory.createSession(true, false, false);
      ClientProducer producer = session.createProducer(managementAddress);
      //todo - https://issues.jboss.org/browse/HORNETQ-1336
      for (SimpleString address : duplicateIDMap.keySet())
      {
         ClientMessage message = session.createMessage(false);
         List<Pair<byte[], Long>> list = duplicateIDMap.get(address);
         String[] array = new String[list.size()];
         for (int i = 0; i < list.size(); i++)
         {
            Pair<byte[], Long> pair = list.get(i);
            array[i] = new String(pair.getA());
         }
         ManagementHelper.putOperationInvocation(message, ResourceNames.CORE_SERVER, "updateDuplicateIdCache", address.toString(), array);
         producer.send(message);
      }
      session.close();
   }

   /**
    * Loop through every *other* queue on this address to see if it also contains this message.
    * Skip queues with filters that don't match as matching messages will never be in there.
    * Also skip queues that we've already checked in the "big" loop.
    */
   private void getQueuesWithMessage(PagingStore store, List<Queue> queues, Map<SimpleString, LinkedListIterator<MessageReference>> queueIterators, List<SimpleString> checkedQueues, Queue bigLoopQueue, List<Queue> queuesWithMessage, MessageReference bigLoopRef, long messageId) throws Exception
   {
      for (Queue queue : queues)
      {
         if (!checkedQueues.contains(queue.getName()) &&
            ((queue.getFilter() == null &&
               bigLoopQueue.getFilter() == null) ||
               (queue.getFilter() != null &&
                  queue.getFilter().equals(bigLoopQueue.getFilter()))))
         {
            // an optimization for paged messages, eliminates the need to (potentially) scan the whole queue
            if (bigLoopRef.isPaged())
            {
               PageSubscription subscription = store.getCursorProvider().getSubscription(queue.getID());
               if (subscription.contains((PagedReference) bigLoopRef))
               {
                  queuesWithMessage.add(queue);
               }
            }
            else
            {
               LinkedListIterator<MessageReference> queueIterator = queueIterators.get(queue.getName());
               boolean first = true;
               long initialMessageID = 0;
               while (queueIterator.hasNext())
               {
                  Message m = queueIterator.next().getMessage();
                  if (first)
                  {
                     initialMessageID = m.getMessageID();
                     first = false;
                  }
                  if (m.getMessageID() == messageId)
                  {
                     queuesWithMessage.add(queue);
                     break;
                  }
               }

               /**
                * if we've reached the end then reset the iterator and go through again until we
                * get back to the place where we started
                */
               if (!queueIterator.hasNext())
               {
                  queueIterator = queue.totalIterator();
                  queueIterators.put(queue.getName(), queueIterator);
                  while (queueIterator.hasNext())
                  {
                     Message m = queueIterator.next().getMessage();
                     if (m.getMessageID() == initialMessageID)
                     {
                        break;
                     }
                     else if (m.getMessageID() == messageId)
                     {
                        queuesWithMessage.add(queue);
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Get the ID of the queues involved so the message can be routed properly.  This is done because we cannot
    * send directly to a queue, we have to send to an address instead but not all the queues related to the
    * address may need the message
    */
   private long createQueueIfNecessaryAndGetID(ClientSession session, Queue queue, SimpleString addressName) throws Exception
   {
      long queueID = getQueueID(session, queue.getName());
      if (queueID == -1)
      {
         session.createQueue(addressName, queue.getName(), queue.getFilter() == null ? null : queue.getFilter().getFilterString(), queue.isDurable());
         HornetQServerLogger.LOGGER.debug("Failed to get queue ID, creating queue [addressName=" + addressName + ", queueName=" + queue.getName() + ", filter=" + (queue.getFilter() == null ? "" : queue.getFilter().getFilterString()) + ", durable=" + queue.isDurable() + "]");
         queueID = getQueueID(session, queue.getName());
      }

      HornetQServerLogger.LOGGER.debug("ID for " + queue + " is: " + queueID);
      return queueID;
   }

   private Integer getQueueID(ClientSession session, SimpleString queueName) throws Exception
   {
      Integer queueID = -1;
      ClientRequestor requestor = new ClientRequestor(session, "jms.queue.hornetq.management");
      ClientMessage managementMessage = session.createMessage(false);
      ManagementHelper.putAttribute(managementMessage, "core.queue." + queueName, "ID");
      session.start();
      HornetQServerLogger.LOGGER.debug("Requesting ID for: " + queueName);
      ClientMessage reply = requestor.request(managementMessage);
      Object result = ManagementHelper.getResult(reply);
      if (result != null && result instanceof Integer)
      {
         queueID = (Integer) result;
      }
      requestor.close();
      return queueID;
   }

   public static class OrderQueueByNumberOfReferencesComparator implements Comparator<Queue>
   {
      @Override
      public int compare(Queue queue1, Queue queue2)
      {
         final int BEFORE = -1;
         final int EQUAL = 0;
         final int AFTER = 1;
         int result = 0;

         if (queue1 == queue2) return EQUAL;

         if (queue1.getMessageCount() == queue2.getMessageCount()) return EQUAL;
         if (queue1.getMessageCount() > queue2.getMessageCount()) return BEFORE;
         if (queue1.getMessageCount() < queue2.getMessageCount()) return AFTER;

         return result;
      }
   }
}
