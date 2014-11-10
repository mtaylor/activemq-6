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

import org.apache.activemq.command.BrokerInfo;
import org.apache.activemq.command.ConnectionControl;
import org.hornetq.core.protocol.openwire.OpenWireConnection;

public interface AMQConnector
{
   /**
    * @return brokerInfo
    */
   BrokerInfo getBrokerInfo();

   /**
    * @return the statistics for this connector
    */
   AMQConnectorStatistics getStatistics();

   /**
    * @return true if update client connections when brokers leave/join a
    *         cluster
    */
   boolean isUpdateClusterClients();

   /**
    * @return true if clients should be re-balanced across the cluster
    */
   boolean isRebalanceClusterClients();

   /**
    * Update all the connections with information about the connected brokers in
    * the cluster
    */
   void updateClientClusterInfo();

   /**
    * @return true if clients should be updated when a broker is removed from a
    *         broker
    */
   boolean isUpdateClusterClientsOnRemove();

   int connectionCount();

   /**
    * If enabled, older connections with the same clientID are stopped
    *
    * @return true/false if link stealing is enabled
    */
   boolean isAllowLinkStealing();

   //see TransportConnector
   ConnectionControl getConnectionControl();

   void onStarted(OpenWireConnection connection);

   void onStopped(OpenWireConnection connection);

}
