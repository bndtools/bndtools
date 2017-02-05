package bndtools.editor.pages;

import org.bndtools.core.ui.ExtendedFormEditor;
import org.bndtools.core.ui.IFormPageFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.editor.IFormPage;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import aQute.bnd.build.model.BndEditModel;
import bndtools.Plugin;
import bndtools.editor.common.MDSashForm;
import bndtools.editor.project.RuntimeJVMArgumentsPart;
import bndtools.editor.project.RuntimeLauncherArgumentsPart;
import bndtools.editor.project.RuntimeOSGiPropertiesPart;
import bndtools.utils.MessageHyperlinkAdapter;

public class ProjectRuntimePage extends FormPage {

    private final BndEditModel model;

    private final Image imgRepos = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "/icons/runtime-24.png").createImage();

    public static final IFormPageFactory FACTORY_PROJECT = new IFormPageFactory() {
        @Override
        public IFormPage createPage(ExtendedFormEditor editor, BndEditModel model, String id) throws IllegalArgumentException {

            return new ProjectRuntimePage(editor, model, id, "Runtime");
        }

        @Override
        public boolean supportsMode(Mode mode) {
            return mode == Mode.project;
        }
    };

    public static final IFormPageFactory FACTORY_BNDRUN = new IFormPageFactory() {
        @Override
        public IFormPage createPage(ExtendedFormEditor editor, BndEditModel model, String id) throws IllegalArgumentException {
            return new ProjectRuntimePage(editor, model, id, "Runtime");
        }

        @Override
        public boolean supportsMode(Mode mode) {
            return mode == Mode.bndrun;
        }
    };

    public ProjectRuntimePage(FormEditor editor, BndEditModel model, String id, String title) {
        super(editor, id, title);
        this.model = model;

    }

    @Override
    protected void createFormContent(IManagedForm managedForm) {
        managedForm.setInput(model);

        FormToolkit tk = managedForm.getToolkit();
        final ScrolledForm form = managedForm.getForm();
        form.setText("Runtime Properties");

        updateFormImage(form);

        tk.decorateFormHeading(form.getForm());
        form.getForm().addMessageHyperlinkListener(new MessageHyperlinkAdapter(getEditor()));

        GridLayout gl;
        GridData gd;

        // Create Controls
        final Composite body = form.getBody();

        MDSashForm sashForm = new MDSashForm(body, SWT.VERTICAL, managedForm);
        sashForm.setSashWidth(6);
        tk.adapt(sashForm);

        final Composite runtime = tk.createComposite(sashForm);
        gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        runtime.setLayoutData(gd);
        gl = new GridLayout(1, true);
        runtime.setLayout(gl);

        RuntimeOSGiPropertiesPart runtimeOSGiPropertiesPart = new RuntimeOSGiPropertiesPart(runtime, tk, Section.TITLE_BAR | Section.EXPANDED | Section.TWISTIE);
        managedForm.addPart(runtimeOSGiPropertiesPart);
        runtimeOSGiPropertiesPart.getSection().setLayoutData(PageLayoutUtils.createExpanded());
        runtimeOSGiPropertiesPart.getSection().addExpansionListener(new ResizeExpansionAdapter(runtimeOSGiPropertiesPart.getSection()));

        RuntimeLauncherArgumentsPart runtimeLauncherArgumentsPart = new RuntimeLauncherArgumentsPart(runtime, tk, Section.TITLE_BAR | Section.EXPANDED | Section.TWISTIE);
        managedForm.addPart(runtimeLauncherArgumentsPart);
        runtimeLauncherArgumentsPart.getSection().setLayoutData(PageLayoutUtils.createExpanded());
        runtimeLauncherArgumentsPart.getSection().addExpansionListener(new ResizeExpansionAdapter(runtimeLauncherArgumentsPart.getSection()));

        RuntimeJVMArgumentsPart runtimeJVMArgumentsPart = new RuntimeJVMArgumentsPart(runtime, tk, Section.TITLE_BAR | Section.EXPANDED | Section.TWISTIE);
        managedForm.addPart(runtimeJVMArgumentsPart);
        runtimeJVMArgumentsPart.getSection().setLayoutData(PageLayoutUtils.createExpanded());
        runtimeJVMArgumentsPart.getSection().addExpansionListener(new ResizeExpansionAdapter(runtimeJVMArgumentsPart.getSection()));

        sashForm.hookResizeListener();
        body.setLayout(new FillLayout());
    }

    @Override
    public void dispose() {
        super.dispose();
        imgRepos.dispose();
    }

    private void updateFormImage(final ScrolledForm form) {
        form.setImage(imgRepos);
    }
}
