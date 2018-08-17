package org.nasdanika.docgen;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.nasdanika.codegen.Folder;
import org.nasdanika.codegen.Project;
import org.nasdanika.codegen.Workspace;

/**
 * Node in the documentation tree.
 * @author Pavel Vlasov
 *
 */
public interface DocumentationNode {
	
	/**
	 * Documentation tree item icon. Can be null.
	 * @return
	 */
	Object getIcon();
	
	/**
	 * Documentation tree item label. Shall not be null.
	 * @return
	 */
	String getLabel();
	
	/**
	 * Builds content generator and returns path to the content entry point to be used by the tree item.
	 * @param workspace Workspace.
	 * @param project Project.
	 * @param docFolder Documentation folder.
	 * @param objectPathResolver Resolves object path by delegating to the root documentation node.
	 * @param iconManager Takes image object, whatever it is, stores known image types to the generation model under "icons" folder and returns icon path. Dedups.
	 * @return
	 */
	String buildContentGenerator(
			Workspace workspace, 
			Project project, 
			Folder docFolder, 
			Function<Object, String> objectPathResolver,
			Function<Object, String> iconManager);
	
	/**
	 * This method is used to resolve inter-node references.
	 * @param object
	 * @return Path to the object documentation relative to the doc folder or null.
	 */
	String getObjectPath(Object object);
	
	List<DocumentationNode> getChildren();
	
	/**
	 * Node id, shall be unique within the tree.
	 * @return
	 */
	String getId();
	
	/**
	 * Accepts a visitor and walks it through the tree.
	 * @param visitor
	 */
	void accept(Consumer<DocumentationNode> visitor);

}
