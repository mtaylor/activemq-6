/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.protocol.mqtt.netty.extensions;

import java.net.SocketAddress;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

public class ActiveMQChannelHandlerContext implements ChannelHandlerContext
{
   private ByteBufAllocator bufferAllocator;

   public static ActiveMQChannelHandlerContext create(ByteBufAllocator bufferAllocator)
   {
      return new ActiveMQChannelHandlerContext(bufferAllocator);
   }

   public static ActiveMQChannelHandlerContext create(ActiveMQBuffer buffer)
   {
      return new ActiveMQChannelHandlerContext(new ActiveMQByteBufAllocator(buffer));
   }

   private ActiveMQChannelHandlerContext(ByteBufAllocator bufferAllocator)
   {
      this.bufferAllocator = bufferAllocator;
   }

   @Override
   public Channel channel()
   {
      return null;
   }

   @Override
   public EventExecutor executor()
   {
      return null;
   }

   @Override
   public String name()
   {
      return null;
   }

   @Override
   public ChannelHandler handler()
   {
      return null;
   }

   @Override
   public boolean isRemoved()
   {
      return false;
   }

   @Override
   public ChannelHandlerContext fireChannelRegistered()
   {
      return null;
   }

   @Override
   public ChannelHandlerContext fireChannelUnregistered()
   {
      return null;
   }

   @Override
   public ChannelHandlerContext fireChannelActive()
   {
      return null;
   }

   @Override
   public ChannelHandlerContext fireChannelInactive()
   {
      return null;
   }

   @Override
   public ChannelHandlerContext fireExceptionCaught(Throwable cause)
   {
      return null;
   }

   @Override
   public ChannelHandlerContext fireUserEventTriggered(Object event)
   {
      return null;
   }

   @Override
   public ChannelHandlerContext fireChannelRead(Object msg)
   {
      return null;
   }

   @Override
   public ChannelHandlerContext fireChannelReadComplete()
   {
      return null;
   }

   @Override
   public ChannelHandlerContext fireChannelWritabilityChanged()
   {
      return null;
   }

   @Override
   public ChannelFuture bind(SocketAddress localAddress)
   {
      return null;
   }

   @Override
   public ChannelFuture connect(SocketAddress remoteAddress)
   {
      return null;
   }

   @Override
   public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress)
   {
      return null;
   }

   @Override
   public ChannelFuture disconnect()
   {
      return null;
   }

   @Override
   public ChannelFuture close()
   {
      return null;
   }

   @Override
   public ChannelFuture deregister()
   {
      return null;
   }

   @Override
   public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise)
   {
      return null;
   }

   @Override
   public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise)
   {
      return null;
   }

   @Override
   public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise)
   {
      return null;
   }

   @Override
   public ChannelFuture disconnect(ChannelPromise promise)
   {
      return null;
   }

   @Override
   public ChannelFuture close(ChannelPromise promise)
   {
      return null;
   }

   @Override
   public ChannelFuture deregister(ChannelPromise promise)
   {
      return null;
   }

   @Override
   public ChannelHandlerContext read()
   {
      return null;
   }

   @Override
   public ChannelFuture write(Object msg)
   {
      return null;
   }

   @Override
   public ChannelFuture write(Object msg, ChannelPromise promise)
   {
      return null;
   }

   @Override
   public ChannelHandlerContext flush()
   {
      return null;
   }

   @Override
   public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise)
   {
      return null;
   }

   @Override
   public ChannelFuture writeAndFlush(Object msg)
   {
      return null;
   }

   @Override
   public ChannelPipeline pipeline()
   {
      return null;
   }

   @Override
   public ByteBufAllocator alloc()
   {
      return bufferAllocator;
   }

   @Override
   public ChannelPromise newPromise()
   {
      return null;
   }

   @Override
   public ChannelProgressivePromise newProgressivePromise()
   {
      return null;
   }

   @Override
   public ChannelFuture newSucceededFuture()
   {
      return null;
   }

   @Override
   public ChannelFuture newFailedFuture(Throwable cause)
   {
      return null;
   }

   @Override
   public ChannelPromise voidPromise()
   {
      return null;
   }

   @Override
   public <T> Attribute<T> attr(AttributeKey<T> key)
   {
      return null;
   }
}
