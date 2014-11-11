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

package org.proton.plug.test.minimalserver;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;


/**
 * @author Clebert Suconic
 */

public class DumbServer
{
   static ConcurrentHashMap<String, BlockingDeque<Object>> maps = new ConcurrentHashMap<>();

   public static BlockingDeque getQueue(String name)
   {
      BlockingDeque q = maps.get(name);
      if (q == null)
      {
         q = new LinkedBlockingDeque();
         BlockingDeque oldValue = maps.putIfAbsent(name, q);
         if (oldValue != null)
         {
            q = oldValue;
         }
      }
      return q;
   }

   public static void clear()
   {
      for (BlockingDeque<Object> queue : maps.values())
      {
         // We clear the queues just in case there is a component holding it
         queue.clear();
      }
      maps.clear();
   }

   public static void put(String queue, Object message)
   {
      getQueue(queue).add(message);
   }

}
