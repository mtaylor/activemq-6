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
package org.hornetq.core.server.cluster.ha;

import org.hornetq.api.core.DiscoveryGroupConfiguration;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.core.client.impl.ServerLocatorInternal;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.server.HornetQMessageBundle;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServerLogger;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScaleDownPolicy
{
   private List<String> connectors = new ArrayList<>();

   private String discoveryGroup = null;

   private String groupName = null;

   private String clusterName;

   private boolean enabled;

   public ScaleDownPolicy()
   {
   }

   public ScaleDownPolicy(List<String> connectors, String groupName, String clusterName, boolean enabled)
   {
      this.connectors = connectors;
      this.groupName = groupName;
      this.clusterName = clusterName;
      this.enabled = enabled;
   }

   public ScaleDownPolicy(String discoveryGroup, String groupName, String clusterName, boolean enabled)
   {
      this.discoveryGroup = discoveryGroup;
      this.groupName = groupName;
      this.clusterName = clusterName;
      this.enabled = enabled;
   }


   public List<String> getConnectors()
   {
      return connectors;
   }

   public void setConnectors(List<String> connectors)
   {
      this.connectors = connectors;
   }

   public String getDiscoveryGroup()
   {
      return discoveryGroup;
   }

   public void setDiscoveryGroup(String discoveryGroup)
   {
      this.discoveryGroup = discoveryGroup;
   }

   public String getGroupName()
   {
      return groupName;
   }

   public void setGroupName(String groupName)
   {
      this.groupName = groupName;
   }

   public String getClusterName()
   {
      return clusterName;
   }

   public void setClusterName(String clusterName)
   {
      this.clusterName = clusterName;
   }

   public boolean isEnabled()
   {
      return enabled;
   }

   public void setEnabled(boolean enabled)
   {
      this.enabled = enabled;
   }

   public static ServerLocatorInternal getScaleDownConnector(ScaleDownPolicy scaleDownPolicy, HornetQServer hornetQServer) throws HornetQException
   {
      if (!scaleDownPolicy.getConnectors().isEmpty())
      {
         return (ServerLocatorInternal) HornetQClient.createServerLocatorWithHA(connectorNameListToArray(scaleDownPolicy.getConnectors(), hornetQServer));
      }
      else if (scaleDownPolicy.getDiscoveryGroup() != null)
      {
         DiscoveryGroupConfiguration dg = hornetQServer.getConfiguration().getDiscoveryGroupConfigurations().get(scaleDownPolicy.getDiscoveryGroup());

         if (dg == null)
         {
            throw HornetQMessageBundle.BUNDLE.noDiscoveryGroupFound(dg);
         }
         return  (ServerLocatorInternal) HornetQClient.createServerLocatorWithHA(dg);
      }
      else
      {
         Map<String, TransportConfiguration> connectorConfigurations = hornetQServer.getConfiguration().getConnectorConfigurations();
         for (TransportConfiguration transportConfiguration : connectorConfigurations.values())
         {
            if (transportConfiguration.getFactoryClassName().equals(InVMConnectorFactory.class.getName()))
            {
               return (ServerLocatorInternal) HornetQClient.createServerLocatorWithHA(transportConfiguration);
            }
         }
      }
      throw HornetQMessageBundle.BUNDLE.noConfigurationFoundForScaleDown();
   }

   private static TransportConfiguration[] connectorNameListToArray(final List<String> connectorNames, HornetQServer hornetQServer)
   {
      TransportConfiguration[] tcConfigs = (TransportConfiguration[]) Array.newInstance(TransportConfiguration.class,
            connectorNames.size());
      int count = 0;
      for (String connectorName : connectorNames)
      {
         TransportConfiguration connector = hornetQServer.getConfiguration().getConnectorConfigurations().get(connectorName);

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
