/**
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Eclipse Public License (EPL).
 * Please see the license.txt included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package org.python.pydev.debug.codecoverage;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.dialogs.ContainerSelectionDialog;
import org.eclipse.ui.texteditor.MarkerUtilities;
import org.python.pydev.core.ExtensionHelper;
import org.python.pydev.debug.pyunit.ViewPartWithOrientation;
import org.python.pydev.editor.PyEdit;
import org.python.pydev.editor.actions.PyOpenAction;
import org.python.pydev.editor.model.ItemPointer;
import org.python.pydev.editor.model.Location;
import org.python.pydev.plugin.PydevPlugin;
import org.python.pydev.tree.AllowValidPathsFilter;
import org.python.pydev.tree.FileTreeLabelProvider;
import org.python.pydev.tree.FileTreePyFilesProvider;
import org.python.pydev.ui.IViewCreatedObserver;
import org.python.pydev.utils.ProgressAction;
import org.python.pydev.utils.ProgressOperation;

/**
 * This sample class demonstrates how to plug-in a new workbench view. The view shows data obtained from the model. The sample creates a
 * dummy model on the fly, but a real implementation would connect to the model available either in this or another plug-in (e.g. the
 * workspace). The view is connected to the model using a content provider.
 * <p>
 * The view uses a label provider to define how model objects should be presented in the view. Each view can present the same model objects
 * using different labels and icons, if needed. Alternatively, a single label provider can be shared between views in order to ensure that
 * objects of the same type are presented in the same way everywhere.
 * <p>
 */

public class PyCodeCoverageView extends ViewPartWithOrientation {
    
    public static String PY_COVERAGE_VIEW_ID = "org.python.pydev.views.PyCodeCoverageView";
    
    //layout stuff
    private Composite leftComposite;

    //actions
    /**
     * double click the tree
     */
    private DoubleClickTreeAction doubleClickAction = new DoubleClickTreeAction();

    /**
     * changed selected element
     */
    private SelectionChangedTreeAction selectionChangedAction = new SelectionChangedTreeAction();

    /**
     * choose new dir
     */
    private ProgressAction chooseAction = new ChooseAction();

    /**
     * clear the results (and erase .coverage file)
     */
    protected ProgressAction clearAction = new ClearAction();

    /**
     * get the new results from the .coverage file
     */
    protected RefreshAction refreshAction = new RefreshAction();

    private Button chooseButton;
    
    //write the results here
    private Text text;

    //tree so that user can browse results.
    private TreeViewer viewer;

    /**
     *  
     */
    private static IContainer lastChosenDir;
    
    private static boolean allRunsDoCoverage = false;


    private SashForm sash;

    private Button allRunsGoThroughCoverage;

    private Label labelErrorFolderNotSelected;

    //Actions ------------------------------
    /**
     * In this action we have to go and refresh all the info based on the chosen dir.
     * 
     * @author Fabio Zadrozny
     */
    private final class RefreshAction extends ProgressAction {
        
        public RefreshAction(){
            this.setText("Refresh coverage information");
        }
        
        public void run() {
            try {
                PyCoverage.getPyCoverage().refreshCoverageInfo(lastChosenDir, this.monitor);

                viewer.setInput(lastChosenDir.getLocation().toFile()); //new files may have been added.
                text.setText("Refreshed info.");
            } catch (Exception e) {
                PydevPlugin.log(e);
            }
        }
    }

    
    /**
     * @return
     */
    public static boolean getAllRunsDoCoverage() {
        return allRunsDoCoverage && lastChosenDir != null && lastChosenDir.exists();
    }
    
    public static IContainer getChosenDir() {
        return lastChosenDir;
    }

    
    
    /**
     * 
     * @author Fabio Zadrozny
     */
    private final class ClearAction extends ProgressAction {
        
        public ClearAction(){
            this.setText("Clear coverage information");
        }
        
        public void run() {

            PyCoverage.getPyCoverage().clearInfo();

            MessageDialog.openInformation(getSite().getShell(), "Cleared", "All the coverage data has been cleared!");

            text.setText("Data cleared (NOT REFRESHED).");
        }
    }

    /**
     * 
     * @author Fabio Zadrozny
     */
    private final class SelectionChangedTreeAction extends Action {
        public void run() {
            run((IStructuredSelection) viewer.getSelection());
        }

        /**
         * @param event
         */
        public void runWithEvent(SelectionChangedEvent event) {
            run((IStructuredSelection) event.getSelection());
        }

        public void run(IStructuredSelection selection) {
            Object obj = selection.getFirstElement();

            if (obj == null)
                return;

            File realFile = new File(obj.toString());
            if (realFile.exists()) {
                text.setText(PyCoverage.getPyCoverage().cache.getStatistics(lastChosenDir, realFile));
            }
        }

    }

    /**
     * 
     * @author Fabio Zadrozny
     */
    private final class DoubleClickTreeAction extends ProgressAction {

        public void run() {
            run(viewer.getSelection());
        }

        /**
         * @param event
         */
        public void runWithEvent(DoubleClickEvent event) {
            run(event.getSelection());
        }

        public void run(ISelection selection) {
            try {
                Object obj = ((IStructuredSelection) selection).getFirstElement();

                File realFile = new File(obj.toString());
                if (realFile.exists() && !realFile.isDirectory()) {
                    ItemPointer p = new ItemPointer(realFile, new Location(-1, -1), null);
                    PyOpenAction act = new PyOpenAction();
                    act.run(p);

                    if (act.editor instanceof PyEdit) {
                        PyEdit e = (PyEdit) act.editor;
                        IEditorInput input = e.getEditorInput();
                        IFile original = (input instanceof IFileEditorInput) ? ((IFileEditorInput) input).getFile() : null;
                        if (original == null)
                            return;
                        IDocument document = e.getDocumentProvider().getDocument(e.getEditorInput());

                        String type = IMarker.PROBLEM;
                        original.deleteMarkers(type, false, 1);

                        String message = "Not Executed";

                        FileNode cache = (FileNode) PyCoverage.getPyCoverage().cache.getFile(realFile);
                        if(cache != null){
                            for (Iterator<Object> it = cache.notExecutedIterator(); it.hasNext();) {
                                Map<String, Object> map = new HashMap<String, Object>();
                                int errorLine = ((Integer) it.next()).intValue() - 1;
    
                                IRegion region = document.getLineInformation(errorLine);
                                int errorEnd = region.getOffset();
                                int errorStart = region.getOffset() + region.getLength();
    
                                map.put(IMarker.MESSAGE, message);
                                map.put(IMarker.SEVERITY, new Integer(IMarker.SEVERITY_ERROR));
                                map.put(IMarker.LINE_NUMBER, new Integer(errorLine));
                                map.put(IMarker.CHAR_START, new Integer(errorStart));
                                map.put(IMarker.CHAR_END, new Integer(errorEnd));
                                map.put(IMarker.TRANSIENT, Boolean.valueOf(true));
                                map.put(IMarker.PRIORITY, new Integer(IMarker.PRIORITY_HIGH));
    
                                MarkerUtilities.createMarker(original, map, type);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 
     * @author Fabio Zadrozny
     */
    private final class ChooseAction extends ProgressAction {
        public void run() {
            ContainerSelectionDialog dialog = new ContainerSelectionDialog(getSite().getShell(), null, false, 
                    "Choose folder to be analyzed in the code-coverage");
            dialog.showClosedProjects(false);
            if (dialog.open() != Window.OK) {
              return;
            }
            Object[] objects = dialog.getResult();
            if (objects.length == 1) { //only one folder can be selected
                if (objects[0] instanceof IPath) {
                    IPath p = (IPath) objects[0];

                    IWorkspace w = ResourcesPlugin.getWorkspace();
                    IContainer folderForLocation = (IContainer) w.getRoot().findMember(p);
                    lastChosenDir = folderForLocation;
                    updateErrorMessages();
                    
                    refreshAction.monitor = this.monitor;
                    refreshAction.run();
                }
            }


        }
    }

    // Class -------------------------------------------------------------------

    /**
     * The constructor.
     */
    public PyCodeCoverageView() {
        @SuppressWarnings("unchecked")
        List<IViewCreatedObserver> participants = ExtensionHelper.getParticipants(
                ExtensionHelper.PYDEV_VIEW_CREATED_OBSERVER);
        for (IViewCreatedObserver iViewCreatedObserver : participants) {
            iViewCreatedObserver.notifyViewCreated(this);
        }
    }

    public void refresh() {
        viewer.refresh();
        getSite().getPage().bringToTop(this);
    }
    
    @Override
    protected void setNewOrientation(int orientation) {
        if(sash != null && !sash.isDisposed() && fParent != null && !fParent.isDisposed()){
            GridLayout layout= (GridLayout) fParent.getLayout();
            if(orientation == VIEW_ORIENTATION_HORIZONTAL){
                sash.setOrientation(SWT.HORIZONTAL);
                layout.numColumns = 2;
                
            }else{
                sash.setOrientation(SWT.VERTICAL);
                layout.numColumns = 1;
            }
            fParent.layout();
        }
    }


    /**
     * This is a callback that will allow us to create the viewer and initialize it.
     */
    public void createPartControl(Composite parent) {
        super.createPartControl(parent);
        allRunsDoCoverage = false;
        
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        layout.verticalSpacing = 2;
        layout.marginWidth = 0;
        layout.marginHeight = 2;
        parent.setLayout(layout);

        sash = new SashForm(parent, SWT.HORIZONTAL);
        GridData layoutData = new GridData();
        layoutData.grabExcessHorizontalSpace = true;
        layoutData.grabExcessVerticalSpace = true;
        layoutData.horizontalAlignment = GridData.FILL;
        layoutData.verticalAlignment = GridData.FILL;
        sash.setLayoutData(layoutData);

        parent = sash;

        leftComposite = new Composite(parent, SWT.MULTI);
        layout = new GridLayout();
        layout.numColumns = 1;
        layout.verticalSpacing = 2;
        layout.marginWidth = 0;
        layout.marginHeight = 2;
        layoutData = new GridData();
        layoutData.grabExcessHorizontalSpace = true;
        layoutData.grabExcessVerticalSpace = true;
        layoutData.horizontalAlignment = GridData.FILL;
        layoutData.verticalAlignment = GridData.FILL;
        leftComposite.setLayoutData(layoutData);
        leftComposite.setLayout(layout);

        text = new Text(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        try {
            text.setFont(new Font(null, "Courier new", 10, 0));
        } catch (Exception e) {
            //ok, might mot be available.
        }
        onControlCreated.call(text);
        layoutData = new GridData();
        layoutData.grabExcessHorizontalSpace = true;
        layoutData.grabExcessVerticalSpace = true;
        layoutData.horizontalAlignment = GridData.FILL;
        layoutData.verticalAlignment = GridData.FILL;
        text.setLayoutData(layoutData);

        parent = leftComposite;
        
        //all the runs from now on go through coverage?
        allRunsGoThroughCoverage = new Button(parent, SWT.CHECK);
        allRunsGoThroughCoverage.setText("Enable code coverage for new launches?");
        allRunsGoThroughCoverage.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                allRunsDoCoverage = allRunsGoThroughCoverage.getSelection();
                updateErrorMessages();
            }
        });
        layoutData = new GridData();
        layoutData.grabExcessHorizontalSpace = true;
        layoutData.horizontalAlignment = GridData.FILL;
        allRunsGoThroughCoverage.setLayoutData(layoutData);
        //end all runs go through coverage

        //choose button
        chooseButton = new Button(parent, SWT.PUSH);
        createButton(parent, chooseButton, "Choose folder to analyze", chooseAction);
        //end choose button

        viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        onControlCreated.call(viewer);
        viewer.setContentProvider(new FileTreePyFilesProvider());
        viewer.setLabelProvider(new FileTreeLabelProvider());
        viewer.addFilter(new AllowValidPathsFilter());

        hookViewerActions();

        layoutData = new GridData();
        layoutData.grabExcessHorizontalSpace = true;
        layoutData.grabExcessVerticalSpace = true;
        layoutData.horizontalAlignment = GridData.FILL;
        layoutData.verticalAlignment = GridData.FILL;
        viewer.getControl().setLayoutData(layoutData);
        
        IActionBars actionBars = getViewSite().getActionBars();
        IToolBarManager toolbarManager = actionBars.getToolBarManager();
        IMenuManager menuManager = actionBars.getMenuManager();
        
        menuManager.add(clearAction);
        menuManager.add(refreshAction);

//        //clear results button
//        clearButton = new Button(parent, SWT.PUSH);
//        createButton(parent, clearButton, "Clear coverage information", clearAction);
//        //end choose button
//
//        //refresh button
//        refreshButton = new Button(parent, SWT.PUSH);
//        createButton(parent, refreshButton, "Refresh coverage information", refreshAction);
//        //end choose button

        this.refresh();
        updateErrorMessages();

    }

    /**
     * Create button with hooked action.
     * 
     * @param parent
     * @param button
     * @param string
     */
    private void createButton(Composite parent, Button button, String txt, final ProgressAction action) {
        GridData layoutData;
        button.setText(txt);
        button.addSelectionListener(new SelectionListener() {

            public void widgetSelected(SelectionEvent e) {
                ProgressOperation.startAction(getSite().getShell(), action, true);
            }

            public void widgetDefaultSelected(SelectionEvent e) {
            }

        });

        layoutData = new GridData();
        layoutData.grabExcessHorizontalSpace = true;
        layoutData.horizontalAlignment = GridData.FILL;
        button.setLayoutData(layoutData);
    }

    /**
     * 
     * Add the double click and selection changed action
     */
    private void hookViewerActions() {
        viewer.addDoubleClickListener(new IDoubleClickListener() {
            public void doubleClick(DoubleClickEvent event) {
                doubleClickAction.runWithEvent(event);
            }
        });

        viewer.addSelectionChangedListener(new ISelectionChangedListener() {

            public void selectionChanged(SelectionChangedEvent event) {
                selectionChangedAction.runWithEvent(event);
            }

        });
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.part.WorkbenchPart#dispose()
     */
    @Override
    public void dispose() {
        allRunsDoCoverage = false;
        super.dispose();
    }

    private void updateErrorMessages() {
        
        boolean showError = false;
        
        if(allRunsDoCoverage){
            if(lastChosenDir == null){
                showError = true;
            }
        }
        if(showError){
            if(labelErrorFolderNotSelected == null){
                labelErrorFolderNotSelected = new Label(leftComposite, SWT.NONE);
                labelErrorFolderNotSelected.setForeground(PydevPlugin.getColorCache().getColor("RED"));
                labelErrorFolderNotSelected.setText("Folder must be selected for launching with coverage.");
                GridData layoutData = new GridData();
                layoutData.grabExcessHorizontalSpace = true;
                layoutData.horizontalAlignment = GridData.FILL;
                labelErrorFolderNotSelected.setLayoutData(layoutData);
           }
        }else{
            if(labelErrorFolderNotSelected != null){
                this.labelErrorFolderNotSelected.dispose();
                this.labelErrorFolderNotSelected = null;
            }
        }
        this.leftComposite.layout();
    }

}