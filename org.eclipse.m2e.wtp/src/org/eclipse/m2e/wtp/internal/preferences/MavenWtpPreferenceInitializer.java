/*******************************************************************************
 * Copyright (c) 2008-2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.m2e.wtp.internal.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.m2e.wtp.MavenWtpPlugin;
import org.eclipse.m2e.wtp.preferences.MavenWtpPreferencesConstants;


/**
 * Maven WTP preferences initializer.
 * 
 * @author Fred Bricon
 */
public class MavenWtpPreferenceInitializer extends AbstractPreferenceInitializer {

  /**
   * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
   */
  @Override
  public void initializeDefaultPreferences() {
    IPreferenceStore store = MavenWtpPlugin.getDefault().getPreferenceStore();
    store.setDefault(MavenWtpPreferencesConstants.P_APPLICATION_XML_IN_BUILD_DIR, true);
    store.setDefault(MavenWtpPreferencesConstants.P_WEB_MAVENARCHIVER_IN_BUILD_DIR, true);
    store.setDefault(MavenWtpPreferencesConstants.P_ENABLE_M2EWTP, true);
  }

}
