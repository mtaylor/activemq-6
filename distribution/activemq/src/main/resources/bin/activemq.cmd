@echo off
rem Licensed to the Apache Software Foundation (ASF) under one
rem or more contributor license agreements.  See the NOTICE file
rem distributed with this work for additional information
rem regarding copyright ownership.  The ASF licenses this file
rem to you under the Apache License, Version 2.0 (the
rem "License"); you may not use this file except in compliance
rem with the License.  You may obtain a copy of the License at
rem 
rem   http://www.apache.org/licenses/LICENSE-2.0
rem 
rem Unless required by applicable law or agreed to in writing,
rem software distributed under the License is distributed on an
rem "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
rem KIND, either express or implied.  See the License for the
rem specific language governing permissions and limitations
rem under the License.

setlocal

if NOT "%ACTIVEMQ_HOME%"=="" goto CHECK_ACTIVEMQ_HOME
PUSHD .
CD %~dp0..
set ACTIVEMQ_HOME=%CD%
POPD

:CHECK_ACTIVEMQ_HOME
if exist "%ACTIVEMQ_HOME%\bin\activemq.cmd" goto CHECK_JAVA

:NO_HOME
echo ACTIVEMQ_HOME environment variable is set incorrectly. Please set ACTIVEMQ_HOME.
goto END

:CHECK_JAVA
set _JAVACMD=%JAVACMD%

if "%JAVA_HOME%" == "" goto NO_JAVA_HOME
if not exist "%JAVA_HOME%\bin\java.exe" goto NO_JAVA_HOME
if "%_JAVACMD%" == "" set _JAVACMD=%JAVA_HOME%\bin\java.exe
goto RUN_JAVA

:NO_JAVA_HOME
if "%_JAVACMD%" == "" set _JAVACMD=java.exe
echo.
echo Warning: JAVA_HOME environment variable is not set.
echo.

:RUN_JAVA

if exist "%STANDALONE_CONF%" (
   echo Calling "%STANDALONE_CONF%"
   call "%STANDALONE_CONF%" %*
) else (
   echo Config file not found "%STANDALONE_CONF%"
)

set JVM_FLAGS="%JAVA_OPTS% %CLUSTER_PROPS% -Dactivemq.home=%ACTIVEMQ_HOME% -Ddata.dir=%ACTIVEMQ_DATA_DIR% -Djava.util.logging.manager=%ACTIVEMQ_LOG_MANAGER% -Dlogging.configuration=%ACTIVEMQ_LOGGING_CONF% -Djava.library.path="%ACTIVEMQ_HOME%/bin/lib/linux-i686:%ACTIVEMQ_HOME%/bin/lib/linux-x86_64 %DEBUG_ARGS%"

set JVM_FLAGS=%JVM_FLAGS% %JMX_OPTS% -Dactivemq.home="%ACTIVEMQ_HOME%" -classpath "%ACTIVEMQ_HOME%\lib\*"

"%_JAVACMD%" %JVM_FLAGS% org.apache.activemq.cli.ActiveMQ %*

:END
endlocal
GOTO :EOF

:EOF
