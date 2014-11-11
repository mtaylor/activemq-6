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
package org.apache.activemq6.core.config.ha;

import org.apache.activemq6.api.config.HornetQDefaultConfiguration;
import org.apache.activemq6.core.config.HAPolicyConfiguration;

public class ReplicatedPolicyConfiguration implements HAPolicyConfiguration
{
   private boolean checkForLiveServer = HornetQDefaultConfiguration.isDefaultCheckForLiveServer();

   private String groupName = null;

   private String clusterName = null;

   public ReplicatedPolicyConfiguration()
   {
   }

   @Override
   public TYPE getType()
   {
      return TYPE.REPLICATED;
   }

   public boolean isCheckForLiveServer()
   {
      return checkForLiveServer;
   }

   public ReplicatedPolicyConfiguration setCheckForLiveServer(boolean checkForLiveServer)
   {
      this.checkForLiveServer = checkForLiveServer;
      return this;
   }

   public String getGroupName()
   {
      return groupName;
   }

   public ReplicatedPolicyConfiguration setGroupName(String groupName)
   {
      this.groupName = groupName;
      return this;
   }

   public String getClusterName()
   {
      return clusterName;
   }

   public ReplicatedPolicyConfiguration setClusterName(String clusterName)
   {
      this.clusterName = clusterName;
      return this;
   }
}
