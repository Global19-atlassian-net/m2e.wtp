/*************************************************************************************
 * Copyright (c) 2012-2015 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Fred Bricon (Red Hat, Inc.) - initial API and implementation
 ************************************************************************************/
package org.eclipse.m2e.wtp.jpa.internal.configurators;

import java.io.File;
import java.util.List;

import org.apache.maven.model.Resource;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jpt.common.core.internal.resource.ModuleResourceLocator;
import org.eclipse.jpt.common.core.internal.resource.SimpleJavaResourceLocator;
import org.eclipse.jpt.common.core.internal.resource.WebModuleResourceLocator;
import org.eclipse.jpt.common.core.resource.ResourceLocator;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.osgi.util.NLS;
import org.eclipse.wst.common.componentcore.ModuleCoreNature;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IProjectFacet;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maven resource Locator
 *
 * @author Fred Bricon
 */
@SuppressWarnings("restriction")
public class MavenResourceLocator implements ResourceLocator {

	private static final Logger LOG = LoggerFactory.getLogger(MavenResourceLocator.class);

	private static IPath META_INF_PATH = new Path("META-INF"); //$NON-NLS-1$

	private static final IProjectFacet WEB_FACET = ProjectFacetsManager.getProjectFacet("jst.web"); //$NON-NLS-1$

	/**
	 * Accepts all resources not under the build output and test build output
	 * folders
	 */
	@Override
	public boolean locationIsValid(IProject project, IContainer container) {
		IMavenProjectFacade mavenProjectFacade = getMavenProjectFacade(project);
		boolean accept = true;
		if (mavenProjectFacade != null
				&& mavenProjectFacade.getMavenProject() != null) {
			IPath classesPath = mavenProjectFacade.getOutputLocation();
			IPath testClassesPath = mavenProjectFacade.getTestOutputLocation();
			if (classesPath.isPrefixOf(container.getFullPath())
					|| testClassesPath.isPrefixOf(container.getFullPath())) {
				// Reject everything coming from target/classes and
				// target/testClasses
				accept = false;
			}
		} else {
			// Maven project not loaded yet, fallback to default behaviour.
			//System.err.println(project + " not loaded");
			accept = getDelegate(project).locationIsValid(project, container);
		}
		// Sometimes src/main/resources/META-INF is not even sent immediately to
		// this method, resulting in persistence.xml not being added to the jpaFiles
		// of the jpaProject hence the creation of a
		// "The persistence.xml file does not have recognized content." error
		// marker

		return accept;
	}

	private ResourceLocator getDelegate(IProject project) {
		if (ModuleCoreNature.isFlexibleProject(project)) {
			try {
				IFacetedProject facetedProject = ProjectFacetsManager.create(project);
				if (facetedProject != null && facetedProject.hasProjectFacet(WEB_FACET)) {
					return new WebModuleResourceLocator();
				}
			} catch (CoreException e) {
				//Ignore
			}
			return new ModuleResourceLocator();
		}
		return new SimpleJavaResourceLocator();
	}

	/**
	 * Returns the resource path from Maven's resource folders mapped to the
	 * runtimePath.
	 */
	@Override
	public IPath getWorkspacePath(IProject project, IPath runtimePath) {
		IMavenProjectFacade mavenProjectFacade = getMavenProjectFacade(project);
		IPath resourcePath = null;
		if (mavenProjectFacade != null) {
			resourcePath = lookupMavenResources(mavenProjectFacade, runtimePath);
		} else {
			// Maven project not loaded yet, we fallback on the JavaProject
			// source folders lookup
			resourcePath = lookupProjectSources(project, runtimePath);
		}

		if (resourcePath == null) {
			resourcePath = getDelegate(project).getWorkspacePath(project, runtimePath);
		}
		return resourcePath;
	}

	IPath lookupMavenResources(IMavenProjectFacade mavenProjectFacade, IPath runtimePath) {
		if (mavenProjectFacade == null) {
			return null;
		}
		IPath resourcePath = null;
		if (mavenProjectFacade.getMavenProject() != null) {
			List<Resource> resources = mavenProjectFacade.getMavenProject().getBuild().getResources();
			for (Resource resourceFolder : resources) {
				resourcePath = getFilePath(getWorkspaceRelativePath(resourceFolder), runtimePath);
				if (resourcePath != null) {
					return resourcePath;
				}
			}
		}

		return lookupProjectSources(mavenProjectFacade.getProject(), runtimePath);
	}

	IPath lookupProjectSources(IProject project, IPath runtimePath) {
		IJavaProject javaProject = JavaCore.create(project);
		IPath resourcePath = null;
		try {
			for (IClasspathEntry entry : javaProject.getRawClasspath()) {
				if (IClasspathEntry.CPE_SOURCE == entry.getEntryKind()) {
					resourcePath = getFilePath(entry.getPath(), runtimePath);
					if (resourcePath != null) {
						return resourcePath;
					}
				}
			}
		} catch (JavaModelException e) {
			LOG.error(NLS.bind("An error occured while looking up {0} sources",project),e); //$NON-NLS-1$
		}
		return null;
	}

	private IPath getFilePath(IPath containerPath, IPath runtimePath) {
		if (containerPath != null) {
			final IWorkspaceRoot root = ResourcesPlugin.getWorkspace()
					.getRoot();
			IFile resource = root.getFile(containerPath.append(runtimePath));
			if (resource.exists()) {
				return resource.getFullPath();
			}
		}
		return null;
	}

	/**
	 * Returns the META-INF folder found in one of Maven's resource.
	 */
	@Override
	public IContainer getDefaultLocation(IProject project) {
		IMavenProjectFacade mavenProjectFacade = getMavenProjectFacade(project);
		IContainer defaultLocation = null;
		if (mavenProjectFacade != null	&& mavenProjectFacade.getMavenProject() != null) {
			final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			for (Resource resourceFolder : mavenProjectFacade.getMavenProject().getBuild().getResources()) {
				IPath p = getWorkspaceRelativePath(resourceFolder);
				if (p != null) {
					IFolder candidate = root.getFolder(p.append(META_INF_PATH));
					if (candidate.exists()) {
						return candidate;
					}
					if (defaultLocation == null) {
						defaultLocation = candidate;
					}
				}
			}
		}

		if (defaultLocation == null) {
			defaultLocation = getDelegate(project).getDefaultLocation(project);
		}
		return defaultLocation;
	}

	private IPath getWorkspaceRelativePath(Resource mavenResourceFolder) {
		File resourceDirectory = new File(mavenResourceFolder.getDirectory());
		IPath relativePath = null;
		if (resourceDirectory.exists() && resourceDirectory.isDirectory()) {
			relativePath = getWorkspaceRelativePath(mavenResourceFolder.getDirectory());
		}
		return relativePath;
	}

	private IPath getWorkspaceRelativePath(String absolutePath) {
		final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IPath relativePath = new Path(absolutePath).makeRelativeTo(root
				.getLocation());
		return relativePath;
	}

	/**
	 * Returns the cached IMavenProjectFacade in m2e's project registry
	 */
	private IMavenProjectFacade getMavenProjectFacade(IProject project) {
		return MavenPlugin.getMavenProjectRegistry().create(project.getFile(IMavenConstants.POM_FILE_NAME),
															true,
															new NullProgressMonitor());
	}

	@Override
	public IPath getRuntimePath(IProject project, IPath resourcePath) {
		IPath runtimePath = getDelegate(project).getRuntimePath(project, resourcePath);
		return runtimePath;
	}

}
