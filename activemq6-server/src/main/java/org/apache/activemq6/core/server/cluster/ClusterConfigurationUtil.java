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
package org.apache.activemq6.core.server.cluster;


import org.apache.activemq6.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq6.api.core.TransportConfiguration;
import org.apache.activemq6.core.config.ClusterConnectionConfiguration;
import org.apache.activemq6.core.config.Configuration;
import org.apache.activemq6.core.server.HornetQServerLogger;

import java.lang.reflect.Array;
import java.util.List;

public class ClusterConfigurationUtil
{
   public static TransportConfiguration getTransportConfiguration(ClusterConnectionConfiguration config, Configuration configuration)
   {
      if (config.getName() == null)
      {
         HornetQServerLogger.LOGGER.clusterConnectionNotUnique();

         return null;
      }

      if (config.getAddress() == null)
      {
         HornetQServerLogger.LOGGER.clusterConnectionNoForwardAddress();

         return null;
      }

      TransportConfiguration connector = configuration.getConnectorConfigurations().get(config.getConnectorName());

      if (connector == null)
      {
         HornetQServerLogger.LOGGER.clusterConnectionNoConnector(config.getConnectorName());
         return null;
      }
      return connector;
   }

   public static DiscoveryGroupConfiguration getDiscoveryGroupConfiguration(ClusterConnectionConfiguration config, Configuration configuration)
   {
      DiscoveryGroupConfiguration dg = configuration.getDiscoveryGroupConfigurations()
            .get(config.getDiscoveryGroupName());

      if (dg == null)
      {
         HornetQServerLogger.LOGGER.clusterConnectionNoDiscoveryGroup(config.getDiscoveryGroupName());
         return null;
      }
      return dg;
   }

   public static TransportConfiguration[] getTransportConfigurations(ClusterConnectionConfiguration config, Configuration configuration)
   {
      return config.getStaticConnectors() != null ? connectorNameListToArray(config.getStaticConnectors(), configuration)
            : null;
   }

   public static TransportConfiguration[] connectorNameListToArray(final List<String> connectorNames, Configuration configuration)
   {
      TransportConfiguration[] tcConfigs = (TransportConfiguration[]) Array.newInstance(TransportConfiguration.class,
            connectorNames.size());
      int count = 0;
      for (String connectorName : connectorNames)
      {
         TransportConfiguration connector = configuration.getConnectorConfigurations().get(connectorName);

         if (connector == null)
         {
            HornetQServerLogger.LOGGER.bridgeNoConnector(connectorName);

            return null;
         }

         tcConfigs[count++] = connector;
      }

      return tcConfigs;
   }
}
