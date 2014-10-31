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

package org.hornetq.core.server.cluster;


import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.core.client.impl.ClientSessionFactoryInternal;
import org.hornetq.core.config.ClusterConnectionConfiguration;
import org.hornetq.core.config.ConfigurationUtils;
import org.hornetq.core.protocol.core.Channel;
import org.hornetq.core.protocol.core.CoreRemotingConnection;
import org.hornetq.core.protocol.core.impl.ChannelImpl;
import org.hornetq.core.protocol.core.impl.PacketImpl;
import org.hornetq.core.protocol.core.impl.wireformat.BackupRegistrationMessage;
import org.hornetq.core.protocol.core.impl.wireformat.BackupRequestMessage;
import org.hornetq.core.protocol.core.impl.wireformat.BackupResponseMessage;
import org.hornetq.core.protocol.core.impl.wireformat.ClusterConnectMessage;
import org.hornetq.core.protocol.core.impl.wireformat.ClusterConnectReplyMessage;
import org.hornetq.core.protocol.core.impl.wireformat.NodeAnnounceMessage;
import org.hornetq.core.protocol.core.impl.wireformat.QuorumVoteMessage;
import org.hornetq.core.protocol.core.impl.wireformat.QuorumVoteReplyMessage;
import org.hornetq.core.protocol.core.impl.wireformat.ScaleDownAnnounceMessage;
import org.hornetq.core.server.HornetQMessageBundle;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServerLogger;
import org.hornetq.core.server.cluster.qourum.QuorumVoteHandler;
import org.hornetq.core.server.cluster.qourum.Vote;

/**
 * handles the communication between a cluster node and the cluster, either the whole cluster or a specific node in the
 * cluster such as a replicating node.
 */
public class ClusterControl implements AutoCloseable
{
   private Channel clusterChannel;

   private final ClientSessionFactoryInternal sessionFactory;

   private final HornetQServer server;

   private final String clusterUser;

   private final String clusterPassword;

   public ClusterControl(ClientSessionFactoryInternal sessionFactory, HornetQServer server)
   {
      this.sessionFactory = sessionFactory;
      this.server = server;
      this.clusterUser = server.getConfiguration().getClusterUser();
      this.clusterPassword = server.getConfiguration().getClusterPassword();
   }

   /**
    * authorise this cluster control so it can communicate with the cluster, it will set the cluster channel on a successful
    * authentication.
    *
    * @throws HornetQException if authorisation wasn't successful.
    */
   public void authorize() throws HornetQException
   {
      CoreRemotingConnection connection = (CoreRemotingConnection)sessionFactory.getConnection();

      clusterChannel = connection.getChannel(ChannelImpl.CHANNEL_ID.CLUSTER.id, -1);

      ClusterConnectReplyMessage packet =
            (ClusterConnectReplyMessage) clusterChannel.sendBlocking(new ClusterConnectMessage(clusterUser, clusterPassword), PacketImpl.CLUSTER_CONNECT_REPLY);

      if (!packet.isAuthorized())
      {
         throw HornetQMessageBundle.BUNDLE.unableToValidateClusterUser(clusterUser);
      }
   }

   /**
    * XXX HORNETQ-720
    *
    * @param attemptingFailBack if {@code true} then this server wants to trigger a fail-back when
    *                           up-to-date, that is it wants to take over the role of 'live' from the current 'live'
    *                           server.
    * @throws org.hornetq.api.core.HornetQException
    */
   public void announceReplicatingBackupToLive(final boolean attemptingFailBack) throws HornetQException
   {

      ClusterConnectionConfiguration config = ConfigurationUtils.getReplicationClusterConfiguration(server.getConfiguration());
      if (config == null)
      {
         HornetQServerLogger.LOGGER.announceBackupNoClusterConnections();
         throw new HornetQException("lacking cluster connection");

      }
      TransportConfiguration connector = server.getConfiguration().getConnectorConfigurations().get(config.getConnectorName());

      if (connector == null)
      {
         HornetQServerLogger.LOGGER.announceBackupNoConnector(config.getConnectorName());
         throw new HornetQException("lacking cluster connection");
      }

      clusterChannel.send(new BackupRegistrationMessage(connector, clusterUser, clusterPassword, attemptingFailBack));
   }

   /**
    * announce this node to the cluster.
    *
    * @param currentEventID used if multiple announcements about this node are made.
    * @param nodeID the node id if the announcing node
    * @param backupGroupName the backup group name.
    * @param scaleDownGroupName the scaledown group name
    * @param isBackup are we a backup
    * @param config the transports config
    * @param backupConfig the transports backup config
    */
   public void sendNodeAnnounce(final long currentEventID,
                                String nodeID,
                                String backupGroupName,
                                String scaleDownGroupName,
                                boolean isBackup,
                                TransportConfiguration config,
                                TransportConfiguration backupConfig)
   {
      clusterChannel.send(new NodeAnnounceMessage(currentEventID, nodeID, backupGroupName, scaleDownGroupName, isBackup, config, backupConfig));
   }

   /**
    * create a replication channel
    *
    * @return the replication channel
    */
   public Channel createReplicationChannel()
   {
      CoreRemotingConnection connection = (CoreRemotingConnection)sessionFactory.getConnection();
      return connection.getChannel(ChannelImpl.CHANNEL_ID.REPLICATION.id, -1);
   }

   /**
    * get the session factory used to connect to the cluster
    *
    * @return the session factory
    */
   public ClientSessionFactoryInternal getSessionFactory()
   {
      return sessionFactory;
   }

   /**
    * close this cluster control and its resources
    */
   public void close()
   {
      sessionFactory.close();
   }

   public Vote sendQuorumVote(SimpleString handler, Vote vote)
   {
      try
      {
         QuorumVoteReplyMessage replyMessage = (QuorumVoteReplyMessage)
               clusterChannel.sendBlocking(new QuorumVoteMessage(handler, vote), PacketImpl.QUORUM_VOTE_REPLY);
         QuorumVoteHandler voteHandler = server.getClusterManager().getQuorumManager().getVoteHandler(replyMessage.getHandler());
         replyMessage.decodeRest(voteHandler);
         return replyMessage.getVote();
      }
      catch (HornetQException e)
      {
         return null;
      }
   }

   public boolean requestReplicatedBackup(int backupSize, SimpleString nodeID)
   {
      BackupRequestMessage backupRequestMessage = new BackupRequestMessage(backupSize, nodeID);
      return requestBackup(backupRequestMessage);
   }

   private boolean requestBackup(BackupRequestMessage backupRequestMessage)
   {
      BackupResponseMessage packet;
      try
      {
         packet = (BackupResponseMessage) clusterChannel.sendBlocking(backupRequestMessage, PacketImpl.BACKUP_REQUEST_RESPONSE);
      }
      catch (HornetQException e)
      {
         return false;
      }
      return packet.isBackupStarted();
   }

   public boolean requestSharedStoreBackup(int backupSize, String journalDirectory, String bindingsDirectory, String largeMessagesDirectory, String pagingDirectory)
   {
      BackupRequestMessage backupRequestMessage = new BackupRequestMessage(backupSize, journalDirectory, bindingsDirectory, largeMessagesDirectory, pagingDirectory);
      return requestBackup(backupRequestMessage);
   }

   public void announceScaleDown(SimpleString targetNodeId, SimpleString scaledDownNodeId)
   {
      ScaleDownAnnounceMessage announceMessage = new ScaleDownAnnounceMessage(targetNodeId, scaledDownNodeId);
      clusterChannel.send(announceMessage);
   }
}
