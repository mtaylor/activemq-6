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

import javax.transaction.xa.Xid;

import org.hornetq.core.persistence.StorageManager;
import org.hornetq.core.protocol.openwire.AMQTransactionImpl;
import org.hornetq.core.transaction.Transaction;
import org.hornetq.core.transaction.TransactionFactory;

public class AMQTransactionFactory implements TransactionFactory
{
   @Override
   public Transaction newTransaction(Xid xid, StorageManager storageManager, int timeoutSeconds)
   {
      return new AMQTransactionImpl(xid, storageManager, timeoutSeconds);
   }
}
