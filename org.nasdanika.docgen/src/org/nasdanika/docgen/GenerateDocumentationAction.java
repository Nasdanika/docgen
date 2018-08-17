package org.nasdanika.docgen;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.nasdanika.codegen.Generator;
import org.nasdanika.codegen.ReconcileAction;
import org.nasdanika.codegen.Work;
import org.nasdanika.config.Context;

/**
 * Base class for documentation generation actions. Uses {@link Generator}s for generation.
 * @author Pavel Vlasov
 *
 */
public abstract class GenerateDocumentationAction extends Action {

	protected ClassLoader getClassLoader() {
		return getClass().getClassLoader();
	}

	public GenerateDocumentationAction(String name) {
		super(name);
	}
	
	protected abstract Generator<?> getGenerator();

	/**
	 * Verify that generation is possible for the selection and collect source objects.
	 * @param selection
	 * @return
	 */
	protected boolean updateSelection(IStructuredSelection selection) {
		return true;
	}	
	
	@Override
	public void run() {
		Generator<?> generator = getGenerator();
		if (generator != null) {
			IWorkbench workbench = PlatformUI.getWorkbench();
			Shell shell = workbench.getModalDialogShellProvider().getShell();
			
			Diagnostician diagnostician = new Diagnostician();
			
			BasicDiagnostic accumulator = new BasicDiagnostic();
			accumulator.add(diagnostician.validate(generator));
			
			IStatus validationStatus = BasicDiagnostic.toIStatus(accumulator);
			if (validationStatus.getSeverity() == IStatus.ERROR) {
	            ErrorDialog.openError(shell, "Generation model is invalid", "Generation model contains errors", validationStatus);
				Activator.getDefault().getLog().log(validationStatus);
				return;
			}
			
			try {							
				
				Map<String, Object> properties = new HashMap<>();
				properties.put("base-url", "irrelevant"); // TODO - spec file or something like this.
				
				Predicate<Object> overwritePredicate = (obj) -> {
					
					int[] result = { 0 };
				
					shell.getDisplay().syncExec(() -> {
						WorkbenchLabelProvider wlp = new WorkbenchLabelProvider();
						MessageDialog dialog = new MessageDialog(
								shell, 
								"Confirm overwrite "+obj.getClass().getName(), 
								null, 
								"Overwrite "+wlp.getText(obj), MessageDialog.QUESTION_WITH_CANCEL, 
								0, 
								new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL });
						
						result[0] = dialog.open();
 					});
					
					if (result[0] == 2) { // index of the cancel button.
						throw new OperationCanceledException();
					}
					
					return result[0] == 0;
				};
				
				properties.put(ReconcileAction.OVERWRITE_PREDICATE_CONTEXT_PROPERTY_NAME, overwritePredicate);
				
				Context rootContext = new Context() {
	
					@Override
					public Object get(String name) {
						if (properties.containsKey(name)) {
							return properties.get(name);
						}
						
						String[] result = { null };					
						shell.getDisplay().syncExec(() -> {						
						    InputDialog id = new InputDialog(shell, "Property value",  "Provide value for property '"+name+"'", null, null);
					        if (id.open() == Window.OK) {
					        	result[0] = id.getValue();
					        }																		
						});
						properties.put(name, result[0]);
						return result[0];
					}
	
					@Override
					public <T> T get(Class<T> type) {						
						return null;
					}
	
					@Override
					public ClassLoader getClassLoader() {
						return GenerateDocumentationAction.this.getClassLoader();
					}
					
				};
				
				WorkspaceModifyOperation operation = new WorkspaceModifyOperation() {
					
					@Override
					protected void execute(IProgressMonitor monitor) throws CoreException, InvocationTargetException, InterruptedException {	
						try {
							Work<?> work = generator.createWork();
							SubMonitor subMonitor = SubMonitor.convert(monitor, work.size());
							work.execute(rootContext, subMonitor);
						} catch (CoreException | InvocationTargetException | InterruptedException | RuntimeException e) {
							throw e;
						} catch (Exception e) {
							throw new InvocationTargetException(e);
						} finally {
							monitor.done();
						}					
					}
					
				};
	
				new ProgressMonitorDialog(shell).run(true, true, operation);
			} catch (Exception exception) {
	            MultiStatus status = createMultiStatus(exception.toString(), exception);
	            ErrorDialog.openError(shell, "Generation error", exception.toString(), status);
				Activator.getDefault().getLog().log(status);
				exception.printStackTrace();
			}
		}
	}
	
	private static MultiStatus createMultiStatus(String msg, Throwable t) {
		List<Status> childStatuses = new ArrayList<>();

		for (StackTraceElement stackTrace : t.getStackTrace()) {
			childStatuses.add(new Status(IStatus.ERROR, "org.nasdanika.codegen.editor", stackTrace.toString()));
		}

		if (t.getCause() != null) {
			childStatuses.add(createMultiStatus("Caused by: " + t.getCause(), t.getCause()));
		}

		for (Throwable s : t.getSuppressed()) {
			childStatuses.add(createMultiStatus("Supressed: " + s, s.getCause()));
		}

		MultiStatus ms = new MultiStatus("org.nasdanika.codegen.editor", IStatus.ERROR,	childStatuses.toArray(new Status[childStatuses.size()]), msg, t);

		return ms;
	}

}
