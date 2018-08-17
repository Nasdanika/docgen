package org.nasdanika.docgen;

import java.util.function.Supplier;

import org.nasdanika.codegen.CodegenFactory;
import org.nasdanika.codegen.Folder;
import org.nasdanika.codegen.Generator;
import org.nasdanika.codegen.Project;
import org.nasdanika.codegen.ReconcileAction;
import org.nasdanika.codegen.Workspace;

/**
 * Generates target project and folder. Subclasses shall override buildGenerator() method.
 * @author Pavel Vlasov
 *
 */
public class BaseDocumentationGeneratorSupplier implements Supplier<Generator<?>> {
	
	private String projectName;
	private String folderPath;

	public BaseDocumentationGeneratorSupplier(String projectName, String folderPath) {
		this.projectName = projectName;
		this.folderPath = folderPath;
	}

	@Override
	public Generator<?> get() {
		Workspace workspace = CodegenFactory.eINSTANCE.createWorkspace();
		
		Project project = CodegenFactory.eINSTANCE.createProject();
		project.setName(projectName);
		workspace.getElements().add(project);
		
		Folder docFolder = CodegenFactory.eINSTANCE.createFolder();
		docFolder.setName(folderPath);
		docFolder.setReconcileAction(ReconcileAction.OVERWRITE);
		project.getResources().add(docFolder);
		
		buildGenerator(workspace, project, docFolder);
		
		return workspace;
	}

	/**
	 * Override this method to provide additional generation facilities.
	 * @param workspace Workspace.
	 * @param project Target project.
	 * @param docFolder Target folder.
	 */
	protected void buildGenerator(Workspace workspace, Project project, Folder docFolder) {
		
		
	}
	

}
