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

import org.hornetq.api.config.HornetQDefaultConfiguration;
import org.hornetq.core.server.impl.Activation;
import org.hornetq.core.server.impl.HornetQServerImpl;
import org.hornetq.core.server.impl.SharedNothingBackupActivation;

import java.util.Map;

public class ReplicaPolicy extends BackupPolicy
{
   private String clusterName;

   private int maxSavedReplicatedJournalsSize = HornetQDefaultConfiguration.getDefaultMaxSavedReplicatedJournalsSize();

   private String groupName = null;

   private boolean restartBackup = HornetQDefaultConfiguration.isDefaultRestartBackup();

   private ReplicatedPolicy replicatedPolicy;

   public ReplicaPolicy()
   {
   }

   public ReplicaPolicy(String clusterName, int maxSavedReplicatedJournalsSize, String groupName, boolean restartBackup, boolean allowFailback, long failbackDelay, ScaleDownPolicy scaleDownPolicy)
   {
      this.clusterName = clusterName;
      this.maxSavedReplicatedJournalsSize = maxSavedReplicatedJournalsSize;
      this.groupName = groupName;
      this.restartBackup = restartBackup;
      this.scaleDownPolicy = scaleDownPolicy;
      //todo check default settings
      replicatedPolicy = new ReplicatedPolicy(false, allowFailback, failbackDelay, groupName, clusterName, this);
   }

   public ReplicaPolicy(String clusterName, int maxSavedReplicatedJournalsSize, String groupName, ReplicatedPolicy replicatedPolicy)
   {
      this.clusterName = clusterName;
      this.maxSavedReplicatedJournalsSize = maxSavedReplicatedJournalsSize;
      this.groupName = groupName;
      this.replicatedPolicy = replicatedPolicy;
   }

   public String getClusterName()
   {
      return clusterName;
   }

   public void setClusterName(String clusterName)
   {
      this.clusterName = clusterName;
   }

   public int getMaxSavedReplicatedJournalsSize()
   {
      return maxSavedReplicatedJournalsSize;
   }

   public void setMaxSavedReplicatedJournalsSize(int maxSavedReplicatedJournalsSize)
   {
      this.maxSavedReplicatedJournalsSize = maxSavedReplicatedJournalsSize;
   }

   public ReplicatedPolicy getReplicatedPolicy()
   {
      return replicatedPolicy;
   }

   public void setReplicatedPolicy(ReplicatedPolicy replicatedPolicy)
   {
      this.replicatedPolicy = replicatedPolicy;
   }

   /*
   * these 2 methods are the same, leaving both as the second is correct but the first is needed until more refactoring is done
   * */
   public String getBackupGroupName()
   {
      return groupName;
   }

   public String getGroupName()
   {
      return groupName;
   }

   public void setGroupName(String groupName)
   {
      this.groupName = groupName;
   }

   public boolean isRestartBackup()
   {
      return restartBackup;
   }

   public void setRestartBackup(boolean restartBackup)
   {
      this.restartBackup = restartBackup;
   }

   @Override
   public boolean isSharedStore()
   {
      return false;
   }

   @Override
   public boolean canScaleDown()
   {
      return scaleDownPolicy != null;
   }

   @Override
   public Activation createActivation(HornetQServerImpl server, boolean wasLive,
                                      Map<String, Object> activationParams,
                                      HornetQServerImpl.ShutdownOnCriticalErrorListener shutdownOnCriticalIO) throws Exception
   {
      SharedNothingBackupActivation backupActivation = new SharedNothingBackupActivation(server, wasLive, activationParams, shutdownOnCriticalIO, this);
      backupActivation.init();
      return backupActivation;
   }
}
