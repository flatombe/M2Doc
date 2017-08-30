/*******************************************************************************
 *  Copyright (c) 2016 Obeo. 
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *   
 *   Contributors:
 *       Obeo - initial API and implementation
 *  
 *******************************************************************************/

package org.obeonetwork.m2doc.ide;

import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.EMFPlugin;
import org.eclipse.emf.common.util.ResourceLocator;
import org.obeonetwork.m2doc.ide.services.DeclaredServicesListener;
import org.obeonetwork.m2doc.ide.services.configurator.ServicesConfiguratorRegistryListener;
import org.osgi.framework.BundleContext;

/**
 * Plugin's activator class.
 * 
 * @author PGUILET_OBEO
 */
public class M2DocPlugin extends EMFPlugin {

    /**
     * Plugin's id.
     */
    public static final String PLUGIN_ID = "org.obeonetwork.m2doc"; //$NON-NLS-1$

    /**
     * The shared instance.
     */
    public static final M2DocPlugin INSTANCE = new M2DocPlugin();

    /**
     * The implementation plugin for Eclipse.
     */
    private static Implementation plugin;

    /**
     * The constructor.
     */
    public M2DocPlugin() {
        super(new ResourceLocator[] {});
    }

    @Override
    public ResourceLocator getPluginResourceLocator() {
        return plugin;
    }

    public static Implementation getPlugin() {
        return plugin;
    }

    /**
     * Class implementing the EclipsePlugin instance, instanciated when the code is run in an OSGi context.
     * 
     * @author cedric
     */
    public static class Implementation extends EclipsePlugin {

        /** The registry listener that will be used to listen to extension changes. */
        private ServicesConfiguratorRegistryListener registryListener = new ServicesConfiguratorRegistryListener();
        /** The listener for M2Doc services. */
        private DeclaredServicesListener servicesListener = new DeclaredServicesListener();

        /**
         * Create the Eclipse Implementation.
         */
        public Implementation() {
            super();

            // Remember the static instance.
            //
            plugin = this;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.core.runtime.Plugin#start(org.osgi.framework.BundleContext)
         */
        @Override
        public void start(BundleContext context) throws Exception {
            super.start(context);
            final IExtensionRegistry registry = Platform.getExtensionRegistry();
            registry.addListener(registryListener,
                    ServicesConfiguratorRegistryListener.SERVICES_CONFIGURATOR_EXTENSION_POINT);
            registryListener.parseInitialContributions();
            registry.addListener(this.servicesListener);
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.core.runtime.Plugin#stop(org.osgi.framework.BundleContext)
         */
        @Override
        public void stop(BundleContext context) throws Exception {
            super.stop(context);
            final IExtensionRegistry registry = Platform.getExtensionRegistry();
            registry.removeListener(registryListener);
            registry.removeListener(this.servicesListener);
            // TODO clear registry and registryListener ?
        }
    }

    /**
     * Returns the shared instance.
     *
     * @return the shared instance.
     */
    public static M2DocPlugin getDefault() {
        return INSTANCE;
    }

    /**
     * Logs the given exception as error or warning.
     * 
     * @param exception
     *            The exception to log.
     * @param blocker
     *            <code>True</code> if the message must be logged as error, <code>False</code> to log it as a
     *            warning.
     */
    public static void log(Exception exception, boolean blocker) {
        int severity = IStatus.WARNING;
        if (blocker) {
            severity = IStatus.ERROR;
        }
        M2DocPlugin.INSTANCE.log(new Status(severity, PLUGIN_ID, exception.getMessage(), exception));
    }

    /**
     * Puts the given message in the error log view, as error or warning.
     * 
     * @param message
     *            The message to put in the error log view.
     * @param blocker
     *            <code>True</code> if the message must be logged as error, <code>False</code> to log it as a
     *            warning.
     */
    public static void log(String message, boolean blocker) {
        int severity = IStatus.WARNING;
        if (blocker) {
            severity = IStatus.ERROR;
        }
        String errorMessage = message;
        if (errorMessage == null || "".equals(errorMessage)) { //$NON-NLS-1$
            errorMessage = "Logging null message should never happens."; //$NON-NLS-1$
        }
        M2DocPlugin.INSTANCE.log(new Status(severity, PLUGIN_ID, errorMessage));
    }
}
