package bndtools.editor.project;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;

import aQute.bnd.osgi.Constants;
import bndtools.editor.common.BndEditorPart;
import bndtools.editor.utils.ToolTips;
import bndtools.utils.ModificationLock;

public class RuntimeLauncherArgumentsPart extends BndEditorPart {

    private final ModificationLock lock = new ModificationLock();

    private String programArgs = null;

    private Text txtProgramArgs;

    private static final String[] SUBCRIBE_PROPS = new String[] {
            Constants.RUNPROGRAMARGS
    };

    public RuntimeLauncherArgumentsPart(Composite parent, FormToolkit toolkit, int style) {
        super(parent, toolkit, style);
        createSection(getSection(), toolkit);
    }

    private void createSection(Section section, FormToolkit toolkit) {
        section.setText("Launcher Arguments");

        final Composite composite = toolkit.createComposite(section);
        section.setClient(composite);

        // Create controls: program args
        txtProgramArgs = toolkit.createText(composite, "", SWT.MULTI | SWT.BORDER);
        ToolTips.setupMessageAndToolTipFromSyntax(txtProgramArgs, Constants.RUNPROGRAMARGS);

        // Layout
        GridLayout gl;
        GridData gd;

        gl = new GridLayout(2, false);
        gl.marginWidth = 0;
        gl.marginHeight = 0;
        composite.setLayout(gl);

        gd = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        gd.heightHint = 40;
        gd.widthHint = 50;
        txtProgramArgs.setLayoutData(gd);
        txtProgramArgs.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent ev) {
                lock.ifNotModifying(new Runnable() {
                    @Override
                    public void run() {
                        markDirty();
                        programArgs = txtProgramArgs.getText();
                        if (programArgs.length() == 0)
                            programArgs = null;
                    }
                });
            }
        });

    }

    @Override
    protected String[] getProperties() {
        return SUBCRIBE_PROPS;
    }

    @Override
    protected void refreshFromModel() {

        lock.modifyOperation(new Runnable() {
            @Override
            public void run() {
                programArgs = model.getRunProgramArgs();
                if (programArgs == null)
                    programArgs = ""; //$NON-NLS-1$
                txtProgramArgs.setText(programArgs);
            }
        });
    }

    @Override
    protected void commitToModel(boolean onSave) {
        model.setRunProgramArgs(emptyToNull(programArgs));
    }

    private String emptyToNull(String s) {
        if (s != null && s.isEmpty())
            return null;
        return s;
    }
}
