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
package org.hornetq.utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class FactoryFinder
{
   /**
    * The strategy that the FactoryFinder uses to find load and instantiate Objects
    * can be changed out by calling the
    * {@link org.hornetq.utils.FactoryFinder#setObjectFactory(org.hornetq.utils.FactoryFinder.ObjectFactory)}
    * method with a custom implementation of ObjectFactory.
    * <p/>
    * The default ObjectFactory is typically changed out when running in a specialized container
    * environment where service discovery needs to be done via the container system.  For example,
    * in an OSGi scenario.
    */
   public interface ObjectFactory
   {
      /**
       * @param path the full service path
       * @return
       */
      Object create(String path) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException;

   }

   /**
    * The default implementation of Object factory which works well in standalone applications.
    */
   protected static class StandaloneObjectFactory implements ObjectFactory
   {
      final ConcurrentHashMap<String, Class> classMap = new ConcurrentHashMap<String, Class>();

      public Object create(final String path) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException
      {
         Class clazz = classMap.get(path);
         if (clazz == null)
         {
            clazz = loadClass(loadProperties(path));
            classMap.put(path, clazz);
         }
         return clazz.newInstance();
      }

      static Class loadClass(Properties properties) throws ClassNotFoundException, IOException
      {

         String className = properties.getProperty("class");
         if (className == null)
         {
            throw new IOException("Expected property is missing: class");
         }
         Class clazz = null;
         ClassLoader loader = Thread.currentThread().getContextClassLoader();
         if (loader != null)
         {
            try
            {
               clazz = loader.loadClass(className);
            }
            catch (ClassNotFoundException e)
            {
               // ignore
            }
         }
         if (clazz == null)
         {
            clazz = FactoryFinder.class.getClassLoader().loadClass(className);
         }

         return clazz;
      }

      public Properties loadProperties(String uri) throws IOException
      {
         // lets try the thread context class loader first
         ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
         if (classLoader == null)
         {
            classLoader = StandaloneObjectFactory.class.getClassLoader();
         }
         InputStream in = classLoader.getResourceAsStream(uri);
         if (in == null)
         {
            in = FactoryFinder.class.getClassLoader().getResourceAsStream(uri);
            if (in == null)
            {
               throw new IOException("Could not find factory class for resource: " + uri);
            }
         }

         // lets load the file
         BufferedInputStream reader = null;
         try
         {
            reader = new BufferedInputStream(in);
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
         }
         finally
         {
            try
            {
               reader.close();
            }
            catch (Exception e)
            {
            }
         }
      }
   }

   // ================================================================
   // Class methods and properties
   // ================================================================
   private static ObjectFactory objectFactory = new StandaloneObjectFactory();

   public static ObjectFactory getObjectFactory()
   {
      return objectFactory;
   }

   public static void setObjectFactory(ObjectFactory objectFactory)
   {
      FactoryFinder.objectFactory = objectFactory;
   }

   // ================================================================
   // Instance methods and properties
   // ================================================================
   private final String path;

   public FactoryFinder(String path)
   {
      this.path = path;
   }

   /**
    * Creates a new instance of the given key
    *
    * @param key is the key to add to the path to find a text file containing
    *            the factory name
    * @return a newly created instance
    */
   public Object newInstance(String key) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException
   {
      return objectFactory.create(path + key);
   }
}
