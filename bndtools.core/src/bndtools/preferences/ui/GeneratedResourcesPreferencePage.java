package bndtools.preferences.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bndtools.api.NamedPlugin;
import org.bndtools.headless.build.manager.api.HeadlessBuildManager;
import org.bndtools.versioncontrol.ignores.manager.api.VersionControlIgnoresManager;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import bndtools.Plugin;
import bndtools.preferences.BndPreferences;
import bndtools.utils.ModificationLock;

public class GeneratedResourcesPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    private final ModificationLock lock = new ModificationLock();

    private final HeadlessBuildManager headlessBuildManager = Plugin.getDefault().getHeadlessBuildManager();
    private final VersionControlIgnoresManager versionControlIgnoresManager = Plugin.getDefault().getVersionControlIgnoresManager();

    private String enableSubs;
    private boolean noAskPackageInfo = false;
    private boolean headlessBuildCreate = true;
    private final Map<String,Boolean> headlessBuildPlugins = new HashMap<String,Boolean>();
    private boolean versionControlIgnoresCreate = true;
    private final Map<String,Boolean> versionControlIgnoresPlugins = new HashMap<String,Boolean>();

    @Override
    public void init(IWorkbench workbench) {
        BndPreferences prefs = new BndPreferences();

        enableSubs = prefs.getEnableSubBundles();
        noAskPackageInfo = prefs.getNoAskPackageInfo();
        headlessBuildCreate = prefs.getHeadlessBuildCreate();

        Collection<NamedPlugin> pluginsInformation = headlessBuildManager.getAllPluginsInformation();
        if (pluginsInformation.size() > 0) {
            headlessBuildPlugins.clear();
            headlessBuildPlugins.putAll(prefs.getHeadlessBuildPlugins(pluginsInformation, false));
        }
        versionControlIgnoresCreate = prefs.getVersionControlIgnoresCreate();
        pluginsInformation = versionControlIgnoresManager.getAllPluginsInformation();
        if (pluginsInformation.size() > 0) {
            versionControlIgnoresPlugins.clear();
            versionControlIgnoresPlugins.putAll(prefs.getVersionControlIgnoresPlugins(versionControlIgnoresManager.getAllPluginsInformation(), false));
        }
    }

    private void checkValid() {
        boolean valid = true;

        if (headlessBuildCreate) {
            Collection<NamedPlugin> pluginsInformation = headlessBuildManager.getAllPluginsInformation();
            if (pluginsInformation.size() > 0) {
                boolean atLeastOneEnabled = false;
                for (Boolean b : headlessBuildPlugins.values()) {
                    atLeastOneEnabled = atLeastOneEnabled || b.booleanValue();
                }
                if (!atLeastOneEnabled) {
                    valid = false;
                    setErrorMessage(Messages.BndPreferencePage_msgCheckValidHeadless);
                }
            }
        }
        if (valid && versionControlIgnoresCreate) {
            Collection<NamedPlugin> pluginsInformation = versionControlIgnoresManager.getAllPluginsInformation();
            if (pluginsInformation.size() > 0) {
                boolean atLeastOneEnabled = false;
                for (Boolean b : versionControlIgnoresPlugins.values()) {
                    atLeastOneEnabled = atLeastOneEnabled || b.booleanValue();
                }
                if (!atLeastOneEnabled) {
                    valid = false;
                    setErrorMessage(Messages.BndPreferencePage_msgCheckValidVersionControlIgnores);
                }
            }
        }

        if (valid) {
            setErrorMessage(null);
        }
        setValid(valid);
    }

    @Override
    public boolean performOk() {
        BndPreferences prefs = new BndPreferences();
        prefs.setEnableSubBundles(enableSubs);
        prefs.setNoAskPackageInfo(noAskPackageInfo);
        prefs.setHeadlessBuildCreate(headlessBuildCreate);
        Collection<NamedPlugin> pluginsInformation = headlessBuildManager.getAllPluginsInformation();
        if (pluginsInformation.size() > 0) {
            prefs.setHeadlessBuildPlugins(headlessBuildPlugins);
        }
        prefs.setVersionControlIgnoresCreate(versionControlIgnoresCreate);
        pluginsInformation = versionControlIgnoresManager.getAllPluginsInformation();
        if (pluginsInformation.size() > 0) {
            prefs.setVersionControlIgnoresPlugins(versionControlIgnoresPlugins);
        }
        return true;
    }

    @Override
    protected Control createContents(Composite parent) {
        GridLayout layout;
        GridData gd;

        Composite composite = new Composite(parent, SWT.NONE);
        layout = new GridLayout(1, false);
        composite.setLayout(layout);

        // Create controls
        Group enableSubBundlesGroup = new Group(composite, SWT.NONE);
        enableSubBundlesGroup.setText(Messages.BndPreferencePage_titleSubBundles);

        final Button btnAlways = new Button(enableSubBundlesGroup, SWT.RADIO);
        btnAlways.setText(Messages.BndPreferencePage_optionAlwaysEnable);
        final Button btnNever = new Button(enableSubBundlesGroup, SWT.RADIO);
        btnNever.setText(Messages.BndPreferencePage_optionNeverEnable);
        Button btnPrompt = new Button(enableSubBundlesGroup, SWT.RADIO);
        btnPrompt.setText(Messages.BndPreferencePage_optionPrompt);

        gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        enableSubBundlesGroup.setLayoutData(gd);

        layout = new GridLayout(1, false);
        enableSubBundlesGroup.setLayout(layout);

        Group exportsGroup = new Group(composite, SWT.NONE);
        exportsGroup.setText(Messages.BndPreferencePage_exportsGroup);

        gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        exportsGroup.setLayoutData(gd);
        layout = new GridLayout(1, false);
        layout.verticalSpacing = 10;
        exportsGroup.setLayout(layout);

        final Button btnNoAskPackageInfo = new Button(exportsGroup, SWT.CHECK);
        btnNoAskPackageInfo.setText(Messages.BndPreferencePage_btnNoAskPackageInfo);

        Collection<NamedPlugin> allPluginsInformation = headlessBuildManager.getAllPluginsInformation();
        if (allPluginsInformation.size() > 0) {
            Group headlessMainGroup = new Group(composite, SWT.NONE);
            headlessMainGroup.setText(Messages.BndPreferencePage_headlessGroup);

            final Button btnHeadlessCreate = new Button(headlessMainGroup, SWT.CHECK);
            btnHeadlessCreate.setText(Messages.BndPreferencePage_headlessCreate_text);
            btnHeadlessCreate.setSelection(headlessBuildCreate);

            final Table headlessTable = new Table(headlessMainGroup, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL);
            
            for (NamedPlugin info : allPluginsInformation) {
                String pluginName = info.getName();
                TableItem itemHeadlessPlugin = new TableItem(headlessTable, SWT.NONE);
                itemHeadlessPlugin.setData(pluginName);
                if (info.isDeprecated()) {
                    itemHeadlessPlugin.setText(pluginName + Messages.BndPreferencePage_namedPluginDeprecated_text);
                } else {
                    itemHeadlessPlugin.setText(pluginName);
                }
                Boolean checked = headlessBuildPlugins.get(pluginName);
                if (checked == null) {
                    checked = Boolean.FALSE;
                    headlessBuildPlugins.put(pluginName, checked);
                }
                itemHeadlessPlugin.setChecked(checked.booleanValue());
            }
            
            headlessTable.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (event.detail != SWT.CHECK) {
						return;
					}

					TableItem item = (TableItem) event.item;
					String pluginName = (String) item.getData();
					headlessBuildPlugins.put(pluginName, item.getChecked());
					checkValid();
				}
			});

            gd = new GridData(SWT.FILL, SWT.FILL, true, false);
            headlessTable.setLayoutData(gd);

            gd = new GridData(SWT.FILL, SWT.FILL, true, false);
            headlessMainGroup.setLayoutData(gd);

            layout = new GridLayout(1, true);
            headlessMainGroup.setLayout(layout);

            // Load Data
            if (MessageDialogWithToggle.ALWAYS.equals(enableSubs)) {
                btnAlways.setSelection(true);
                btnNever.setSelection(false);
                btnPrompt.setSelection(false);
            } else if (MessageDialogWithToggle.NEVER.equals(enableSubs)) {
                btnAlways.setSelection(false);
                btnNever.setSelection(true);
                btnPrompt.setSelection(false);
            } else {
                btnAlways.setSelection(false);
                btnNever.setSelection(false);
                btnPrompt.setSelection(true);
            }
            btnNoAskPackageInfo.setSelection(noAskPackageInfo);

            // Listeners
            SelectionAdapter adapter = new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    lock.ifNotModifying(new Runnable() {
                        @Override
                        public void run() {
                            if (btnAlways.getSelection()) {
                                enableSubs = MessageDialogWithToggle.ALWAYS;
                            } else if (btnNever.getSelection()) {
                                enableSubs = MessageDialogWithToggle.NEVER;
                            } else {
                                enableSubs = MessageDialogWithToggle.PROMPT;
                            }
                        }
                    });
                }
            };
            btnAlways.addSelectionListener(adapter);
            btnNever.addSelectionListener(adapter);
            btnPrompt.addSelectionListener(adapter);
            btnNoAskPackageInfo.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    noAskPackageInfo = btnNoAskPackageInfo.getSelection();
                }
            });
            btnHeadlessCreate.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    headlessBuildCreate = btnHeadlessCreate.getSelection();
                    headlessTable.setEnabled(headlessBuildCreate);
                    checkValid();
                }
            });
        }

        allPluginsInformation = versionControlIgnoresManager.getAllPluginsInformation();
        if (allPluginsInformation.size() > 0) {
            Group versionControlIgnoresMainGroup = new Group(composite, SWT.NONE);
            versionControlIgnoresMainGroup.setText(Messages.BndPreferencePage_versionControlIgnoresGroup_text);

            final Button btnVersionControlIgnoresCreate = new Button(versionControlIgnoresMainGroup, SWT.CHECK);
            btnVersionControlIgnoresCreate.setText(Messages.BndPreferencePage_versionControlIgnoresCreate_text);
            btnVersionControlIgnoresCreate.setSelection(versionControlIgnoresCreate);

            final Table versionControlIgnoresTable = new Table(versionControlIgnoresMainGroup, SWT.CHECK | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL);

            for (NamedPlugin info : allPluginsInformation) {
                String pluginName = info.getName();
                TableItem itemVersionControlIgnoresPlugin = new TableItem(versionControlIgnoresTable, SWT.NONE);
                itemVersionControlIgnoresPlugin.setData(pluginName);
                if (info.isDeprecated()) {
                    itemVersionControlIgnoresPlugin.setText(pluginName + Messages.BndPreferencePage_namedPluginDeprecated_text);
                } else {
                    itemVersionControlIgnoresPlugin.setText(pluginName);
                }
                Boolean checked = versionControlIgnoresPlugins.get(pluginName);
                if (checked == null) {
                    checked = Boolean.FALSE;
                    versionControlIgnoresPlugins.put(pluginName, checked);
                }
                itemVersionControlIgnoresPlugin.setChecked(checked.booleanValue());
            }
            
            versionControlIgnoresTable.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (event.detail != SWT.CHECK) {
						return;
					}

					TableItem item = (TableItem) event.item;
					String pluginName = (String) item.getData();
                    versionControlIgnoresPlugins.put(pluginName, item.getChecked());
					checkValid();
				}
			});

            gd = new GridData(SWT.FILL, SWT.FILL, true, false);
            versionControlIgnoresTable.setLayoutData(gd);

            gd = new GridData(SWT.FILL, SWT.FILL, true, false);
            versionControlIgnoresMainGroup.setLayoutData(gd);

            layout = new GridLayout(1, true);
            versionControlIgnoresMainGroup.setLayout(layout);

            btnVersionControlIgnoresCreate.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    versionControlIgnoresCreate = btnVersionControlIgnoresCreate.getSelection();
                    versionControlIgnoresTable.setEnabled(versionControlIgnoresCreate);
                    checkValid();
                }
            });
        }
        return composite;
    }

}
