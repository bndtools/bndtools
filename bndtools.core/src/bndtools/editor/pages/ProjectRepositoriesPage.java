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
import bndtools.editor.project.RepositorySelectionPart;
import bndtools.utils.MessageHyperlinkAdapter;

public class ProjectRepositoriesPage extends FormPage {

    private final BndEditModel model;

    private final Image imgRepos = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "/icons/database.png").createImage();

    public static final IFormPageFactory FACTORY_PROJECT = new IFormPageFactory() {
        @Override
        public IFormPage createPage(ExtendedFormEditor editor, BndEditModel model, String id) throws IllegalArgumentException {

            return new ProjectRepositoriesPage(editor, model, id, "Repos", true);
        }

        @Override
        public boolean supportsMode(Mode mode) {
            return mode == Mode.project;
        }
    };

    public static final IFormPageFactory FACTORY_BNDRUN = new IFormPageFactory() {
        @Override
        public IFormPage createPage(ExtendedFormEditor editor, BndEditModel model, String id) throws IllegalArgumentException {
            return new ProjectRepositoriesPage(editor, model, id, "Repos", true);
        }

        @Override
        public boolean supportsMode(Mode mode) {
            return mode == Mode.bndrun;
        }
    };

    public ProjectRepositoriesPage(FormEditor editor, BndEditModel model, String id, String title, boolean supportsResolve) {
        super(editor, id, title);
        this.model = model;

    }

    @Override
    protected void createFormContent(IManagedForm managedForm) {
        managedForm.setInput(model);

        FormToolkit tk = managedForm.getToolkit();
        final ScrolledForm form = managedForm.getForm();
        form.setText("Repositories");

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

        final Composite repos = tk.createComposite(sashForm);
        gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        repos.setLayoutData(gd);
        gl = new GridLayout(1, true);
        repos.setLayout(gl);

        // First column
        RepositorySelectionPart reposPart = new RepositorySelectionPart(getEditor(), repos, tk, Section.NO_TITLE);
        managedForm.addPart(reposPart);
        reposPart.getSection().setLayoutData(PageLayoutUtils.createExpanded());

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
