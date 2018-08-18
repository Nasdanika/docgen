package org.nasdanika.docgen.emf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
import org.eclipse.emf.edit.provider.IItemPropertyDescriptor;
import org.eclipse.emf.edit.provider.IItemPropertySource;
import org.eclipse.emf.edit.provider.ITreeItemContentProvider;
import org.nasdanika.codegen.CodegenFactory;
import org.nasdanika.codegen.CodegenUtil;
import org.nasdanika.codegen.Folder;
import org.nasdanika.codegen.Project;
import org.nasdanika.codegen.ReconcileAction;
import org.nasdanika.codegen.StaticText;
import org.nasdanika.codegen.TextFile;
import org.nasdanika.codegen.Workspace;
import org.nasdanika.docgen.DocumentationNodeImpl;
import org.nasdanika.html.Bootstrap.Style;
import org.nasdanika.html.Fragment;
import org.nasdanika.html.HTMLFactory;
import org.nasdanika.html.ListGroup;
import org.nasdanika.html.Tabs;
import org.nasdanika.html.Tag;
import org.nasdanika.html.Tag.TagName;

/**
 * Base class for {@link EObject} documentation nodes. 
 * It uses {@link AdapterFactory} to obtain label and icon from {@link IItemLabelProvider}, children from {@link ITreeItemContentProvider}, and properties from {@link IItemPropertySource}.
 * @author Pavel Vlasov
 *
 */
public class EObjectDocumentationNode extends DocumentationNodeImpl {
	
	protected EObject eObject;
	protected AdapterFactory adapterFactory;

	public EObjectDocumentationNode(EObject eObject) {
		ResourceSet resourceSet = eObject.eResource().getResourceSet();
		if (resourceSet instanceof IEditingDomainProvider) {
			EditingDomain editingDomain = ((IEditingDomainProvider) resourceSet).getEditingDomain();
			if (editingDomain instanceof AdapterFactoryEditingDomain) {
				adapterFactory = ((AdapterFactoryEditingDomain) editingDomain).getAdapterFactory();
				this.eObject = eObject;
				IItemLabelProvider labelProvider = (IItemLabelProvider) adapterFactory.adapt(eObject, IItemLabelProvider.class);
				if (labelProvider != null) {
					setLabel(labelProvider.getText(eObject));
					setIcon(labelProvider.getImage(eObject));
				}
				ITreeItemContentProvider treeItemContentProvider = (ITreeItemContentProvider) adapterFactory.adapt(eObject, ITreeItemContentProvider.class);
				if (treeItemContentProvider != null) {
					for (Object child: treeItemContentProvider.getChildren(eObject)) {
						if (child instanceof EObject) {
							addChild(EObjectDocumentationNodeFactoryRegistry.INSTANCE.createDocumentationNode((EObject) child));
						}
					}					
				}
			}
		}		
	}
	
	@Override
	public String buildContentGenerator(
			Workspace workspace, 
			Project project, 
			Folder docFolder,
			Function<Object, String> objectPathResolver,
			Function<Object, String> iconManager) {
		
		if (eObject != null && adapterFactory != null) {
			IItemPropertySource propertySource = (IItemPropertySource) adapterFactory.adapt(eObject, IItemPropertySource.class);
			if (propertySource != null) {
				TextFile textFile = CodegenFactory.eINSTANCE.createTextFile();
				docFolder.getChildren().add(textFile);
				textFile.setReconcileAction(ReconcileAction.OVERWRITE);
				textFile.setName(getId()+".html");

				Map<String, List<IItemPropertyDescriptor>> categories = new TreeMap<>();
				List<IItemPropertyDescriptor> uncategorized = new ArrayList<>();
				for (IItemPropertyDescriptor pd: propertySource.getPropertyDescriptors(eObject)) {
					if (pd.isPropertySet(eObject) || isRenderUnsetProperties()) {
						String category = pd.getCategory(eObject);
						if (CodegenUtil.isBlank(category)) {
							uncategorized.add(pd);
						} else {
							List<IItemPropertyDescriptor> cl = categories.get(category);
							if (cl == null) {
								cl = new ArrayList<>();
								categories.put(category, cl);
							}
							cl.add(pd);
						}
					}
				}
				
				StaticText content = CodegenFactory.eINSTANCE.createStaticText();
				HTMLFactory htmlFactory = HTMLFactory.INSTANCE;
				Fragment contentFragment = htmlFactory.fragment();
				Tag header = htmlFactory.tag(TagName.h2);
				String iconLoc = iconManager.apply(getIcon());
				if (iconLoc != null) {
					header.content(TagName.img.create().attribute("src", iconLoc), " ", getLabel());					
				} else {
					header.content(getLabel());
				}				
				contentFragment.content(header);
				contentFragment.content(TagName.div.create("<B>EClass: </B> ", eObject.eClass().getName())); // TODO - link.
				
				EReference containmentReference = eObject.eContainmentFeature();
				if (containmentReference != null) {
					contentFragment.content(TagName.div.create("<B>Role:</B> ", ((EStructuralFeature) containmentReference).getName()));			
				}
				
				// TODO - description - special treatment for annotated features/properties.
				if (categories.isEmpty()) {
					for (IItemPropertyDescriptor pd: uncategorized) {
						contentFragment.content(renderProperty(workspace, project, docFolder, objectPathResolver, iconManager, pd));
					}
				} else {
					Tabs tabs = htmlFactory.tabs();
					Fragment gf = htmlFactory.fragment();
					for (IItemPropertyDescriptor pd: uncategorized) {
						gf.content(renderProperty(workspace, project, docFolder, objectPathResolver, iconManager, pd));
					}
					if (!gf.isEmpty()) {
						tabs.item("General", gf);
					}
					
					for (Entry<String, List<IItemPropertyDescriptor>> ce: categories.entrySet()) {
						Fragment cf = htmlFactory.fragment();
						for (IItemPropertyDescriptor pd: ce.getValue()) {
							cf.content(renderProperty(workspace, project, docFolder, objectPathResolver, iconManager, pd));
						}
						tabs.item(StringEscapeUtils.escapeHtml4(ce.getKey()), cf);						
					}
					
					contentFragment.content(tabs);					
				}
				content.setContent(contentFragment.toString());					
				textFile.getGenerators().add(content);				
				return textFile.getName();
			}
		}
		return super.buildContentGenerator(workspace, project, docFolder, objectPathResolver, iconManager);
	}

	/**
	 * Override to return true in order to render properties which are not set.
	 * @return
	 */
	protected boolean isRenderUnsetProperties() {
		return false;
	}

	/**
	 * Renders property documentation.
	 * @param workspace
	 * @param project
	 * @param docFolder
	 * @param objectPathResolver
	 * @param propertyDescriptor
	 * @return
	 */
	protected Object renderProperty(
			Workspace workspace, 
			Project project, 
			Folder docFolder,
			Function<Object, String> objectPathResolver,
			Function<Object, String> iconManager,
			IItemPropertyDescriptor propertyDescriptor) {
		
		Object value = propertyDescriptor.getPropertyValue(eObject);
		if (value == null) {
			return "";
		}
		HTMLFactory htmlFactory = HTMLFactory.INSTANCE;
		Fragment ret = htmlFactory.fragment(); // TODO - content type?
		ret.content(TagName.h3.create(StringEscapeUtils.escapeHtml4(propertyDescriptor.getDisplayName(eObject))));
		String description = propertyDescriptor.getDescription(eObject);
		if (!CodegenUtil.isBlank(description)) {
			ret.content(htmlFactory.well(description).small());
		}
//		Object feature = propertyDescriptor.getFeature(eObject);
//		if (feature instanceof EStructuralFeature) {
//			ret.content("<B>Role:</B> ", ((EStructuralFeature) feature).getName(), "<P/>");			
//		}
		
		if (value instanceof IItemPropertySource) {
			IItemPropertySource propertySource = (IItemPropertySource) value;
			Object editableValue = propertySource.getEditableValue(eObject);
			if (propertyDescriptor.isMany(eObject) && editableValue instanceof Collection) {
				ListGroup values = htmlFactory.listGroup();
				for (Object el: (Collection<?>) editableValue) {
					values.item(renderPropertyValue(propertyDescriptor, propertySource, el), Style.DEFAULT);
				}
				ret.content(values);
			} else {
				ret.content(TagName.div.create("<B>Value:</B> ", renderPropertyValue(propertyDescriptor, propertySource, editableValue)));											
			}
		} else {
			ret.content("<B>Value:</B> ", renderPropertyValue(propertyDescriptor, null, value), "<P/>");							
		}
		
		return ret;
	}

	/**
	 * Renders property value.
	 * @param propertyDescriptor
	 * @param value
	 * @return
	 */
	protected Object renderPropertyValue(IItemPropertyDescriptor propertyDescriptor, IItemPropertySource propertySource, Object value) {		
		return TagName.div.create(StringEscapeUtils.escapeHtml4(String.valueOf(value))).style().whiteSpace().pre(); // TODO.
	}

}
