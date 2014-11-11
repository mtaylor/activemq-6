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
package org.hornetq.core.protocol.openwire.amq;

import org.hornetq.api.core.SimpleString;
import org.hornetq.core.persistence.OperationContext;
import org.hornetq.core.persistence.StorageManager;
import org.hornetq.core.postoffice.PostOffice;
import org.hornetq.core.security.SecurityStore;
import org.hornetq.core.server.ServerSessionFactory;
import org.hornetq.core.server.impl.HornetQServerImpl;
import org.hornetq.core.server.impl.ServerSessionImpl;
import org.hornetq.core.server.management.ManagementService;
import org.hornetq.core.transaction.ResourceManager;
import org.hornetq.spi.core.protocol.RemotingConnection;
import org.hornetq.spi.core.protocol.SessionCallback;

public class AMQServerSessionFactory implements ServerSessionFactory
{

   @Override
   public ServerSessionImpl createCoreSession(String name, String username,
         String password, int minLargeMessageSize, boolean autoCommitSends,
         boolean autoCommitAcks, boolean preAcknowledge,
         boolean persistDeliveryCountBeforeDelivery, boolean xa,
         RemotingConnection connection, StorageManager storageManager,
         PostOffice postOffice, ResourceManager resourceManager,
         SecurityStore securityStore, ManagementService managementService,
         HornetQServerImpl hornetQServerImpl, SimpleString managementAddress,
         SimpleString simpleString, SessionCallback callback,
         OperationContext context) throws Exception
   {
      return new AMQServerSession(name, username, password, minLargeMessageSize, autoCommitSends,
            autoCommitAcks, preAcknowledge, persistDeliveryCountBeforeDelivery, xa,
            connection, storageManager, postOffice, resourceManager, securityStore,
            managementService, hornetQServerImpl, managementAddress, simpleString, callback,
            context);
   }

}
