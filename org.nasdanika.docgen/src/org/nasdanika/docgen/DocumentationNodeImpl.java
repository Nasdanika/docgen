package org.nasdanika.docgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.nasdanika.codegen.Folder;
import org.nasdanika.codegen.Project;
import org.nasdanika.codegen.Workspace;

/**
 * Base class for documentation nodes. Handles id generation, children management, and delegation of getObjectPath to the children. 
 * @author Pavel Vlasov
 *
 */
public class DocumentationNodeImpl implements DocumentationNode {
	
	private List<DocumentationNode> children = new ArrayList<>();
	private List<DocumentationNode> unmodifiableChildren = Collections.unmodifiableList(children);
	
	private String label;
	private Object icon;
	private DocumentationNodeImpl parent;

	public DocumentationNodeImpl() {
	}	
	
	public DocumentationNodeImpl(String label, Object icon) {
		this.label = label;
		this.icon = icon;
	}
	
	public void setLabel(String label) {
		this.label = label;
	}
	
	public void setIcon(Object icon) {
		this.icon = icon;
	}

	@Override
	public Object getIcon() {
		return icon;
	}
	
	@Override
	public String getLabel() {
		return label;
	}

	/**
	 * Delegates to children. 
	 * Override to add node-specific resolution.
	 */
	@Override
	public String getObjectPath(Object object) {
		for (DocumentationNode child: getChildren()) {
			String ret = child.getObjectPath(object);
			if (ret != null) {
				return ret;
			}
		}
		return null;
	}
	
	protected void setParent(DocumentationNodeImpl parent) {
		this.parent = parent;
	}
	
	public void addChild(DocumentationNode child) {
		children.add(child);
		if (child instanceof DocumentationNodeImpl) {
			((DocumentationNodeImpl) child).setParent(this);
		}
	}

	@Override
	public List<DocumentationNode> getChildren() {
		return unmodifiableChildren;
	}

	@Override
	public String getId() {
		if (parent == null) {
			return null;
		}
		return parent.getId() == null ? String.valueOf(parent.children.indexOf(this)) : parent.getId()+"-"+parent.children.indexOf(this);
	}

	@Override
	public String buildContentGenerator(
			Workspace workspace, 
			Project project, 
			Folder docFolder,
			Function<Object, 
			String> objectPathResolver,
			Function<Object, String> iconManager) {
		// NOP
		return null;
	}

	@Override
	public void accept(Consumer<DocumentationNode> visitor) {
		visitor.accept(this);
		for (DocumentationNode child: children) {
			child.accept(visitor);
		}
		
	}

}
