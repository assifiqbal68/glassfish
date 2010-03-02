/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009-2010 Sun Microsystems, Inc. All rights reserved.
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


package org.glassfish.osgiweb;

import org.glassfish.osgijavaeebase.OSGiDeploymentContext;
import org.glassfish.osgijavaeebase.BundleClassLoader;
import org.glassfish.internal.api.Globals;
import org.glassfish.internal.api.ClassLoaderHierarchy;
import org.glassfish.web.loader.WebappClassLoader;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.api.deployment.OpsParams;
import org.osgi.framework.*;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.io.File;

import com.sun.enterprise.module.common_impl.CompositeEnumeration;

/**
 * @author Sanjeeb.Sahoo@Sun.COM
 */
class OSGiWebDeploymentContext extends OSGiDeploymentContext {

    private static final Logger logger =
            Logger.getLogger(OSGiWebDeploymentContext.class.getPackage().getName());

    public OSGiWebDeploymentContext(ActionReport actionReport,
                                            Logger logger,
                                            ReadableArchive source,
                                            OpsParams params,
                                            ServerEnvironment env,
                                            Bundle bundle) throws Exception {
        super(actionReport, logger, source, params, env, bundle);
    }

    protected void setupClassLoader() throws Exception     {
        final BundleClassLoader delegate1 = new BundleClassLoader(bundle);
        final ClassLoader delegate2 =
                Globals.get(ClassLoaderHierarchy.class).getAPIClassLoader();

//        This does not work because of lack of pernmission to call
//        protected methods.
//        DelegatingClassLoader parent =
//                new DelegatingClassLoader(delegate1.getParent());
//        parent.addDelegate(new ReflectiveClassFinder(delegate1));
//        parent.addDelegate(new ReflectiveClassFinder(delegate2));

        ClassLoader parent = new ClassLoader() {
            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
            {
                try {
                    return delegate1.loadClass(name, resolve);
                } catch (ClassNotFoundException cnfe) {
                    return delegate2.loadClass(name);
                }
            }

            @Override
            public URL getResource(String name)
            {
                URL url = delegate1.getResource(name);
                if (url == null) {
                    url = delegate2.getResource(name);
                }
                return url;
            }

            @Override
            public Enumeration<URL> getResources(String name) throws IOException
            {
                List<Enumeration<URL>> enumerators = new ArrayList<Enumeration<URL>>();
                enumerators.add(delegate1.getResources(name));
                enumerators.add(delegate2.getResources(name));
                return new CompositeEnumeration(enumerators);
            }
        };

        finalClassLoader = new WebappClassLoader(parent){

            // Since we don't set URLs, we need to return appropriate URLs
            // for JSPC to work. See WebappLoader.setClassPath().
            @Override
            public URL[] getURLs()
            {
                return convert((String)bundle.getHeaders().
                        get(org.osgi.framework.Constants.BUNDLE_CLASSPATH));
            }
            private URL[] convert(String bcp) {
                if (bcp == null || bcp.isEmpty()) bcp=".";
                List<URL> urls = new ArrayList<URL>();
                //Bundle-ClassPath entries are separated by ; or ,
                StringTokenizer entries = new StringTokenizer(bcp, ",;");
                String entry;
                while (entries.hasMoreTokens()) {
                    entry = entries.nextToken().trim();
                    if (entry.startsWith("/")) entry = entry.substring(1);
                    try
                    {
                        URL url = new File(getSourceDir(), entry).toURI().toURL();
                        urls.add(url);
                    }
                    catch (MalformedURLException e)
                    {
                        logger.logp(Level.WARNING, "OSGiDeploymentContext", "convert", "Failed to add {0} as classpath because of", new Object[]{entry, e.getMessage()});
                    }
                }
                return urls.toArray(new URL[0]);
            }
        };
        // This does not work. Find out why TempBundleClassLoader loads a
        // separate copy of Globals.class.
//        shareableTempClassLoader = new WebappClassLoader(
// new TempBundleClassLoader(parent));
//        shareableTempClassLoader.start();
        shareableTempClassLoader = finalClassLoader;
        WebappClassLoader.class.cast(finalClassLoader).start();
    }

}
