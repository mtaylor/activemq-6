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
package org.hornetq.api.core.client;

/**
 * A cluster topology listener.
 * <p>
 * Used to get notification of topology events. After adding a listener to the cluster connection,
 * the listener receives {@link #nodeUP(TopologyMember, boolean)} for all the current topology
 * members.
 * @author tim
 */
public interface ClusterTopologyListener
{
   /**
    * Triggered when a node joins the cluster.
    * @param member
    * @param last if the whole cluster topology is being transmitted (after adding the listener to
    *           the cluster connection) this parameter will be {@code true} for the last topology
    *           member.
    */
   void nodeUP(TopologyMember member, boolean last);

   /**
    * Triggered when a node leaves the cluster.
    * @param eventUID
    * @param nodeID the id of the node leaving the cluster
    */
   void nodeDown(long eventUID, String nodeID);
}
