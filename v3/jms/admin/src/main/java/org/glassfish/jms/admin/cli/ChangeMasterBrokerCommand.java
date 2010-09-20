/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.jms.admin.cli;

import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.*;
import org.glassfish.config.support.TargetType;
import org.glassfish.config.support.CommandTarget;
import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.enterprise.util.SystemPropertyConstants;
import com.sun.enterprise.config.serverbeans.*;

import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.admin.RuntimeType;
import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.enterprise.util.SystemPropertyConstants;
import com.sun.enterprise.config.serverbeans.*;
import com.sun.enterprise.config.serverbeans.Cluster;
import com.sun.enterprise.connectors.jms.util.JmsRaUtil;

import org.glassfish.internal.api.ServerContext;
import java.util.Properties;
import java.util.Map;
import java.util.List;
import java.beans.PropertyVetoException;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.component.PerLookup;
import org.jvnet.hk2.config.types.Property;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.TransactionFailure;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.beans.PropertyVetoException;


/**
 * Change JMS Master Broker Command
 *
 */
@Service(name="change-master-broker")
@Scoped(PerLookup.class)
@I18n("change.master.broker")
@ExecuteOn({RuntimeType.DAS, RuntimeType.INSTANCE})
@TargetType({CommandTarget.DAS,CommandTarget.STANDALONE_INSTANCE,CommandTarget.CLUSTER,CommandTarget.CONFIG})

public class ChangeMasterBrokerCommand extends JMSDestination implements AdminCommand {
    final private static LocalStringManagerImpl localStrings = new LocalStringManagerImpl(ChangeMasterBrokerCommand.class);
    // [usemasterbroker] [availability-enabled] [dbvendor] [dbuser] [dbpassword admin] [jdbcurl] [properties props] clusterName

    @Param(name="newmasterbroker", alias="nmb", optional=false)
    String newMasterBroker;

    @Param(primary=true)
    String clusterName;

    @Inject
    Domain domain;

    @Inject
    com.sun.appserv.connectors.internal.api.ConnectorRuntime connectorRuntime;

    @Inject
    ServerContext serverContext;
    Config config;

    /**
     * Executes the command with the command parameters passed as Properties
     * where the keys are the paramter names and the values the parameter values
     *
     * @param context information
     */
    public void execute(AdminCommandContext context) {
        final ActionReport report = context.getActionReport();
        final String newMB = newMasterBroker;
        Cluster cluster =domain.getClusterNamed(clusterName);

        if (cluster == null) {
            report.setMessage(localStrings.getLocalString("configure.jms.cluster.invalidClusterName",
                            "No Cluster by this name has been configured"));
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }

        Server newMBServer = domain.getServerNamed(newMasterBroker);
        if(!cluster.getName().equals(newMBServer.getCluster().getName()))
        {
            report.setMessage(localStrings.getLocalString("configure.jms.cluster.invalidClusterName",
                            "{0} does not belong to the specified cluster", newMasterBroker));
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }

       Nodes nodes = domain.getNodes();
       config = domain.getConfigNamed(cluster.getConfigRef());
       JmsService jmsservice = config.getJmsService();
       Server oldMBServer = null;
       //If Master broker has been set previously using this command, use that master broker as the old MB instance
       //Else use the first configured instance in the cluster list
       if(jmsservice.getMasterBroker() != null){
            oldMBServer = domain.getServerNamed(jmsservice.getMasterBroker());
       }else
       {
           List<Server> serverList = cluster.getInstances();
           if(serverList == null || serverList.size() == 0){
             report.setMessage(localStrings.getLocalString("change.master.broker.invalidCluster",
                            "No servers configured in cluster {0}", clusterName));
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
           }
           oldMBServer = serverList.get(0);
       }

       String oldMasterBrokerPort = JmsRaUtil.getJMSPropertyValue(oldMBServer);
       if(oldMasterBrokerPort == null) {
	      SystemProperty sp = config.getSystemProperty("JMS_PROVIDER_PORT");
	      if(sp != null) oldMasterBrokerPort = sp.getValue();
       }
       if(oldMasterBrokerPort == null) oldMasterBrokerPort = getDefaultJmsHost(jmsservice).getPort();
       String oldMasterBrokerHost = nodes.getNode(oldMBServer.getNode()).getNodeHost();

       String newMasterBrokerPort = JmsRaUtil.getJMSPropertyValue(newMBServer);
       if(newMasterBrokerPort == null) newMasterBrokerPort = getDefaultJmsHost(jmsservice).getPort();
       String newMasterBrokerHost = nodes.getNode(newMBServer.getNode()).getNodeHost();


       String oldMasterBroker = oldMasterBrokerHost + ":" + oldMasterBrokerPort;
       String newMasterBroker = newMasterBrokerHost + ":" + newMasterBrokerPort;
      // System.out.println("1: IN deleteinstanceCheck supplimental oldMasterBroker = " + oldMasterBroker + " newmasterBroker " + newMasterBroker);
       try{
            updateMasterBroker(oldMBServer.getName(), oldMasterBroker, newMasterBroker);
       }catch(Exception e){
                      report.setMessage(localStrings.getLocalString("change.master.broker.CannotChangeMB",
                                    "Unable to change master broker."));
                            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                        return;
                 }

        try {
            ConfigSupport.apply(new SingleConfigCode<JmsService>() {
                public Object run(JmsService param) throws PropertyVetoException, TransactionFailure {

                    param.setMasterBroker(newMB);
                    return param;
                  }
               }, jmsservice);
        } catch(TransactionFailure tfe) {
            report.setMessage(localStrings.getLocalString("change.master.broker.fail",
                            "Unable to update the domain.xml with the new master broker") +
                            " " + tfe.getLocalizedMessage());
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            report.setFailureCause(tfe);
        }
        report.setMessage(localStrings.getLocalString("change.master.broker.success",
                "Master broker change has executed successfully for Cluster {0}.", clusterName));
        report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
    }

   private JmsHost getDefaultJmsHost(JmsService jmsService){

	    JmsHost jmsHost = null;
            String defaultJmsHostName = jmsService.getDefaultJmsHost();
            List jmsHostsList = jmsService.getJmsHost();

            for (int i=0; i < jmsHostsList.size(); i ++)
            {
               JmsHost tmpJmsHost = (JmsHost) jmsHostsList.get(i);
               if (tmpJmsHost != null && tmpJmsHost.getName().equals(defaultJmsHostName))
                     jmsHost = tmpJmsHost;
            }
	    return jmsHost;
      }

     private void updateMasterBroker(String serverName, String oldMasterBroker, String newMasterBroker) throws Exception {
        MQJMXConnectorInfo mqInfo = getMQJMXConnectorInfo(serverName, config,serverContext, domain, connectorRuntime);

         //MBeanServerConnection  mbsc = getMBeanServerConnection(tgtName);
         try {
             MBeanServerConnection mbsc = mqInfo.getMQMBeanServerConnection();
             ObjectName on = new ObjectName(
                 CLUSTER_MONITOR_MBEAN_NAME);
             Object [] params = null;

             String []  signature = new String [] {
                      "java.lang.String",
                      "java.lang.String"};
                        params = new Object [] {oldMasterBroker, newMasterBroker};

         mbsc.invoke(on, "changeMasterBroker", params, signature);
         } catch (Exception e) {
                     logAndHandleException(e, "admin.mbeans.rmb.error_creating_jms_dest");
         } finally {
                     try {
                         if(mqInfo != null) {
                             mqInfo.closeMQMBeanServerConnection();
                         }
                     } catch (Exception e) {
                       handleException(e);
                     }
                 }
     }
}

