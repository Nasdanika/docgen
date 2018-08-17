package org.nasdanika.docgen.codegen;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.nasdanika.codegen.Generator;
import org.nasdanika.config.Configuration;
import org.nasdanika.docgen.DocumentationNodeImpl;
import org.nasdanika.docgen.GenerateDocumentationAction;
import org.nasdanika.docgen.SiteDocumentationGeneratorSupplier;
import org.nasdanika.docgen.emf.EObjectDocumentationNode;
import org.nasdanika.docgen.emf.EObjectDocumentationNodeFactoryRegistry;

public class GenerateCodegenDocumentationAction extends GenerateDocumentationAction implements ISelectionChangedListener {
	
	protected EObject eObject;

	public GenerateCodegenDocumentationAction() {
		super("Generate documentation");
	}
	
	@Override
	protected Generator<?> getGenerator() {
		URI resourceURI = eObject.eResource().getURI();
		URL baseURL = null;			
		try {
			baseURL = new URL(resourceURI.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		Map<String, Object> properties = new HashMap<>();
		properties.put(Configuration.BASE_URL_PROPERTY, baseURL);
						
		IFile modelFile = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(resourceURI.toPlatformString(true)));
		if (modelFile.exists()) {
			IProject project = modelFile.getProject();
			
			// tree						
			DocumentationNodeImpl rootNode = new DocumentationNodeImpl(modelFile.getName(), null);
			rootNode.addChild(EObjectDocumentationNodeFactoryRegistry.INSTANCE.createDocumentationNode(eObject));
			SiteDocumentationGeneratorSupplier siteDocumentationGeneratorSupplier = new SiteDocumentationGeneratorSupplier(project.getName(), "site/codegen-model-doc/"+modelFile.getName(), rootNode);
			return siteDocumentationGeneratorSupplier.get();
		}	
		
		return null;
	}
	
	@Override
	public void selectionChanged(SelectionChangedEvent event) {
		setEnabled(false);
		if (event.getSelection() instanceof IStructuredSelection) {
			IStructuredSelection selection = (IStructuredSelection) event.getSelection();
			if (selection.size() == 1) {
				Object object = AdapterFactoryEditingDomain.unwrap(selection.getFirstElement());
				if (object instanceof Resource) {
					EList<EObject> contents = ((Resource) object).getContents();
					if (contents.size() != 1) {
						return;
					}
					object = contents.get(0);
				}
				if (object instanceof EObject) {
					eObject = (EObject) object;
					setEnabled(true);
				}
			}
		}
	}	
	
	

}
