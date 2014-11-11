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
package org.apache.activemq6.core.postoffice.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.activemq6.api.core.SimpleString;
import org.apache.activemq6.core.postoffice.Address;
import org.apache.activemq6.core.postoffice.AddressManager;
import org.apache.activemq6.core.postoffice.Binding;
import org.apache.activemq6.core.postoffice.Bindings;
import org.apache.activemq6.core.postoffice.BindingsFactory;
import org.apache.activemq6.core.server.HornetQMessageBundle;
import org.apache.activemq6.core.server.HornetQServerLogger;
import org.apache.activemq6.core.transaction.Transaction;
import org.apache.activemq6.core.transaction.TransactionOperationAbstract;
import org.apache.activemq6.utils.ConcurrentHashSet;

/**
 * A simple address manager that maintains the addresses and bindings.
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="jmesnil@redhat.com">Jeff Mesnil</a>
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class SimpleAddressManager implements AddressManager
{
   /**
    * HashMap<Address, Binding>
    */
   private final ConcurrentMap<SimpleString, Bindings> mappings = new ConcurrentHashMap<SimpleString, Bindings>();

   /**
    * HashMap<QueueName, Binding>
    */
   private final ConcurrentMap<SimpleString, Binding> nameMap = new ConcurrentHashMap<SimpleString, Binding>();

   private final ConcurrentHashSet<SimpleString> pendingDeletes = new ConcurrentHashSet<SimpleString>();

   private final BindingsFactory bindingsFactory;

   public SimpleAddressManager(final BindingsFactory bindingsFactory)
   {
      this.bindingsFactory = bindingsFactory;
   }

   public boolean addBinding(final Binding binding) throws Exception
   {
      if (nameMap.putIfAbsent(binding.getUniqueName(), binding) != null || pendingDeletes.contains(binding.getUniqueName()))
      {
         throw HornetQMessageBundle.BUNDLE.bindingAlreadyExists(binding);
      }

      if (HornetQServerLogger.LOGGER.isTraceEnabled())
      {
         HornetQServerLogger.LOGGER.trace("Adding binding " + binding + " with address = " + binding.getUniqueName(), new Exception("trace"));
      }

      return addMappingInternal(binding.getAddress(), binding);
   }

   public Binding removeBinding(final SimpleString uniqueName, Transaction tx) throws Exception
   {
      final Binding binding = nameMap.remove(uniqueName);

      if (binding == null)
      {
         return null;
      }

      if (tx != null)
      {
         pendingDeletes.add(uniqueName);
         tx.addOperation(new TransactionOperationAbstract()
         {

            @Override
            public void afterCommit(Transaction tx)
            {
               pendingDeletes.remove(uniqueName);
            }

            @Override
            public void afterRollback(Transaction tx)
            {
               nameMap.put(uniqueName, binding);
               pendingDeletes.remove(uniqueName);
            }

         });
      }

      removeBindingInternal(binding.getAddress(), uniqueName);

      return binding;
   }

   public Bindings getBindingsForRoutingAddress(final SimpleString address) throws Exception
   {
      return mappings.get(address);
   }

   public Binding getBinding(final SimpleString bindableName)
   {
      return nameMap.get(bindableName);
   }

   public Map<SimpleString, Binding> getBindings()
   {
      return nameMap;
   }

   public Bindings getMatchingBindings(final SimpleString address) throws Exception
   {
      Address add = new AddressImpl(address);

      Bindings bindings = bindingsFactory.createBindings(address);

      for (Binding binding : nameMap.values())
      {
         Address addCheck = new AddressImpl(binding.getAddress());

         if (addCheck.matches(add))
         {
            bindings.addBinding(binding);
         }
      }

      return bindings;
   }

   public void clear()
   {
      nameMap.clear();
      mappings.clear();
   }

   protected void removeBindingInternal(final SimpleString address, final SimpleString bindableName)
   {
      Bindings bindings = mappings.get(address);

      if (bindings != null)
      {
         removeMapping(bindableName, bindings);

         if (bindings.getBindings().isEmpty())
         {
            mappings.remove(address);
         }
      }
   }

   protected Binding removeMapping(final SimpleString bindableName, final Bindings bindings)
   {
      Binding theBinding = null;

      for (Binding binding : bindings.getBindings())
      {
         if (binding.getUniqueName().equals(bindableName))
         {
            theBinding = binding;

            break;
         }
      }

      if (theBinding == null)
      {
         throw new IllegalStateException("Cannot find binding " + bindableName);
      }

      bindings.removeBinding(theBinding);

      return theBinding;
   }

   protected boolean addMappingInternal(final SimpleString address, final Binding binding) throws Exception
   {
      Bindings bindings = mappings.get(address);

      Bindings prevBindings = null;

      if (bindings == null)
      {
         bindings = bindingsFactory.createBindings(address);

         prevBindings = mappings.putIfAbsent(address, bindings);

         if (prevBindings != null)
         {
            bindings = prevBindings;
         }
      }

      bindings.addBinding(binding);

      return prevBindings != null;
   }
}