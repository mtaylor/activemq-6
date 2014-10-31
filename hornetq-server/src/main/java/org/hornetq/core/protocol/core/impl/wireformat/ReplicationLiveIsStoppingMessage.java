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
/**
 *
 */
package org.hornetq.core.protocol.core.impl.wireformat;

import org.hornetq.api.core.HornetQBuffer;
import org.hornetq.core.protocol.core.impl.PacketImpl;

/**
 * Message indicating that the live is stopping (a scheduled stop).
 * <p>
 * The backup starts the fail-over immediately after receiving this.
 */
public final class ReplicationLiveIsStoppingMessage extends PacketImpl
{

   public enum LiveStopping
   {
      /**
       * Notifies the backup that its live is going to stop. The backup will then NOT fail-over if
       * it gets signals from the cluster that its live sent a disconnect.
       */
      STOP_CALLED(0),
      /**
       * Orders the backup to fail-over immediately. Meant as a follow-up message to
       * {@link #STOP_CALLED}.
       */
      FAIL_OVER(1);
      private final int code;

      private LiveStopping(int code)
      {
         this.code = code;
      }
   }

   private int finalMessage;
   private LiveStopping liveStopping;

   public ReplicationLiveIsStoppingMessage()
   {
      super(PacketImpl.REPLICATION_SCHEDULED_FAILOVER);
   }

   /**
    * @param b
    */
   public ReplicationLiveIsStoppingMessage(LiveStopping b)
   {
      this();
      this.liveStopping = b;
   }

   @Override
   public void encodeRest(final HornetQBuffer buffer)
   {
      buffer.writeInt(liveStopping.code);
   }

   @Override
   public void decodeRest(final HornetQBuffer buffer)
   {
      liveStopping = buffer.readInt() == 0 ? LiveStopping.STOP_CALLED : LiveStopping.FAIL_OVER;
   }

   /**
    * The first message is sent to turn-off the quorumManager, which in some cases would trigger a
    * faster fail-over than what would be correct.
    * @return
    */
   public LiveStopping isFinalMessage()
   {
      return liveStopping;
   }

   @Override
   public String toString()
   {
      return super.toString() + ":" + liveStopping;
   }
}
