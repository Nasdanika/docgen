package org.nasdanika.docgen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.core.runtime.Status;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nasdanika.codegen.BinaryFile;
import org.nasdanika.codegen.BundleResource;
import org.nasdanika.codegen.CodegenFactory;
import org.nasdanika.codegen.Folder;
import org.nasdanika.codegen.Project;
import org.nasdanika.codegen.ReconcileAction;
import org.nasdanika.codegen.StaticBytes;
import org.nasdanika.codegen.StaticText;
import org.nasdanika.codegen.TextFile;
import org.nasdanika.codegen.Workspace;
import org.nasdanika.html.ApplicationPanel;
import org.nasdanika.html.Bootstrap.Style;
import org.nasdanika.html.HTMLFactory;
import org.nasdanika.html.RowContainer.Row;
import org.nasdanika.html.Table;
import org.nasdanika.html.Tag;
import org.nasdanika.html.Tag.TagName;
import org.nasdanika.html.Theme;

/**
 * Generates static HTML documentation site.
 * @author Pavel Vlasov
 *
 */
public class SiteDocumentationGeneratorSupplier extends BaseDocumentationGeneratorSupplier {

	private DocumentationNode root;

	/**
	 * 
	 * @param projectName Name of the project to contain generated documentation.
	 * @param folderPath Name of the folder to contain generated documentation.
	 * @param root Root documentation node. It is not rendered in the toc tree, only its children. Root node's label is used as documentation header.
	 */
	public SiteDocumentationGeneratorSupplier(
			String projectName, 
			String folderPath,
			DocumentationNode root) {
		super(projectName, folderPath);
		this.root = root;
	}

	@Override
	protected void buildGenerator(Workspace workspace, Project project, Folder docFolder) {
		// Web resources
		BundleResource webResources = CodegenFactory.eINSTANCE.createBundleResource();
		docFolder.getChildren().add(webResources);
		webResources.setName("resources");
		webResources.setBundle("org.nasdanika.web.resources");
		webResources.setReconcileAction(ReconcileAction.OVERWRITE);
		webResources.getPaths().add("/bootstrap/");
		webResources.getPaths().add("/font-awesome/");
		webResources.getPaths().add("/css/");
		webResources.getPaths().add("/highlight/");
		webResources.getPaths().add("/jstree/");
		webResources.getPaths().add("/js/");
		webResources.getPaths().add("/images/");		
		webResources.getPaths().add("/img/");		

		// Left panel
		BundleResource jsResources = CodegenFactory.eINSTANCE.createBundleResource();
		docFolder.getChildren().add(jsResources);
		jsResources.setReconcileAction(ReconcileAction.OVERWRITE);
		jsResources.setBundle("org.nasdanika.docgen");
		jsResources.getPaths().add("/resources/js/left-panel.js");

		// index.html
		TextFile indexHtml = CodegenFactory.eINSTANCE.createTextFile();
		indexHtml.setName("index.html");
		docFolder.getChildren().add(indexHtml);
		indexHtml.setReconcileAction(ReconcileAction.OVERWRITE);
		StaticText indexText = CodegenFactory.eINSTANCE.createStaticText();
		indexText.setContent(generateIndexHtml());
		indexHtml.getGenerators().add(indexText);
		
		// toc.js
		final JSONObject idMap = new JSONObject();
		JSONArray tree = new JSONArray();
		// Root is not rendered - just a holder for children.

		// Icons
		Folder iconsFolder = CodegenFactory.eINSTANCE.createFolder();
		iconsFolder.setReconcileAction(ReconcileAction.OVERWRITE);
		iconsFolder.setName("icons");
		
		Map<Object, String> iconMap = new HashMap<>();
		Set<String> iconNames = new HashSet<>();
		
		Function<Object, String> iconManager = icon -> {
			if (icon == null) {
				return null;
			}
			String existingIcon = iconMap.get(icon);
			if (existingIcon != null) {
				return existingIcon;
			}
			String iconName;
			BinaryFile iconFile = null;
			if (icon instanceof URL) {
				URL iconURL = (URL) icon;
				String iconFilePath = iconURL.getFile();
				int lastSlash = iconFilePath.lastIndexOf("/");
				iconName = lastSlash == -1 ? iconFilePath : iconFilePath.substring(lastSlash+1);		
				iconFile = CodegenFactory.eINSTANCE.createBinaryFile();
				iconFile.setReconcileAction(ReconcileAction.OVERWRITE);
				iconsFolder.getChildren().add(iconFile);
				StaticBytes content = CodegenFactory.eINSTANCE.createStaticBytes();
				iconFile.getGenerators().add(content);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (InputStream is = iconURL.openStream()) {
					int b;
					while ((b = is.read()) != -1) {
						baos.write(b);
					}
					baos.close();
				} catch (IOException e) {
					Activator.getDefault().getLog().log(new Status(Status.WARNING, "org.nasdanika.docgen", "Unable to store icon: "+icon, e));
				} 
				content.setContent(baos.toByteArray());
			} else {
				return null;
			}
			
			if (iconNames.add(iconName)) {
				String iconPath = "icons/"+iconName;
				iconMap.put(icon, iconPath);
				if (iconFile != null) {
					iconFile.setName(iconName);
				}
				return iconPath;
			}
			int dotIdx = iconName.lastIndexOf('.');
			String prefix = dotIdx == -1 ? iconName : iconName.substring(0, dotIdx);
			String suffix = dotIdx == -1 ? "" : iconName.substring(dotIdx);
			for (int counter = 0; ; ++counter) {
				String altIconName = prefix + "-" + Integer.toString(++counter, Character.MAX_RADIX) + suffix;
				if (iconNames.add(altIconName)) {
					String iconPath = "icons/"+altIconName;
					iconMap.put(icon, iconPath);
					if (iconFile != null) {
						iconFile.setName(altIconName);
					}
					return iconPath;
				}				
			}
			
			
		};

		for (DocumentationNode dn: root.getChildren()) {
			tree.put(createToc(dn, idMap, workspace, project, docFolder, root::getObjectPath, iconManager));
		}
		
		JSONObject toc = new JSONObject();
		toc.put("idMap", idMap);
		toc.put("tree", tree);
		
		TextFile tocJs = CodegenFactory.eINSTANCE.createTextFile();
		tocJs.setName("toc.js");
		docFolder.getChildren().add(tocJs);
		tocJs.setReconcileAction(ReconcileAction.OVERWRITE);
		StaticText tocJsText = CodegenFactory.eINSTANCE.createStaticText();
		tocJsText.setContent("define("+toc+")");
		tocJs.getGenerators().add(tocJsText);
		
		if (!iconsFolder.getChildren().isEmpty()) {
			docFolder.getChildren().add(iconsFolder);
		}
	}
	
	/**
	 * 
	 * @param node
	 * @param idMap
	 * @param workspace
	 * @param project
	 * @param docFolder
	 * @param objectPathResolver
	 * @param iconManager Takes image object, whatever it is, stores known image types to the generation model under "icons" folder and returns icon path. Dedups.
	 * @return
	 */
	protected JSONObject createToc(
			DocumentationNode node, 
			JSONObject idMap, 
			Workspace workspace, 
			Project project, 
			Folder docFolder, 
			Function<Object, String> objectPathResolver,
			Function<Object, String> iconManager) {
		JSONObject ret = new JSONObject();
		ret.put("text", node.getLabel());
		String iconPath = iconManager.apply(node.getIcon());
		if (iconPath != null) {
			ret.put("icon", iconPath);
		}
		
		ret.put("id", node.getId());
		String entryPoint = node.buildContentGenerator(workspace, project, docFolder, objectPathResolver, iconManager);
		idMap.put(node.getId(), entryPoint==null ? "#" : "#router/doc-content/"+entryPoint);

		JSONArray children = new JSONArray();
		
		for (DocumentationNode child: node.getChildren()) {
			children.put(createToc(child, idMap, workspace, project, docFolder, objectPathResolver, iconManager));
		}

		if (children.length() > 0) {
			ret.put("children", children);
		}
		
		return ret;
	}	
	
	protected String generateIndexHtml() {
		HTMLFactory htmlFactory = HTMLFactory.INSTANCE;
		ApplicationPanel appPanel = htmlFactory.applicationPanel()
				.style(Style.INFO) 
				.header(root.getLabel()) // TODO - icon too.
				.headerLink("index.html")
				.style("margin-bottom", "0px")
				.id("docAppPanel");
		
		Table table = htmlFactory.table().style("margin-bottom", "0px");
		Row row = table.row();
		DocumentationPanelFactory documentationPanelFactory = new DocumentationPanelFactory(htmlFactory) {

			@Override
			protected Tag tocDiv() {
				return super.tocDiv().style("overflow-y", "scroll");
			}
			
		};
		row.cell(documentationPanelFactory.leftPanel()).id("left-panel").style("min-width", "17em");
		row.cell("")
			.id("splitter")
			.style("width", "5px")
			.style("min-width", "5px")
			.style("padding", "0px")
			.style("background", "#d9edf7")
			.style("border", "solid 1px #bce8f1")
			.style("cursor", "col-resize");
		row.cell(documentationPanelFactory.rightPanel()).id("right-panel");
				
		appPanel.contentPanel(
				table, 
				htmlFactory.tag(TagName.script, getClass().getResource("Splitter.js")),
				htmlFactory.tag(TagName.script, getClass().getResource("Scroller.js")),
				htmlFactory.tag(TagName.script, getClass().getResource("SetDimensions.js")));
		
		AutoCloseable app = htmlFactory.bootstrapRouterApplication(
				Theme.Default,
				"Documentation", 
				null, //"main/doc/index.html", 
				htmlFactory.fragment(
						// --- Stylesheets ---					
						htmlFactory.tag(TagName.link)
							.attribute("rel", "stylesheet")
							.attribute("href", "resources/bootstrap/css/bootstrap.min.css"),							
						htmlFactory.tag(TagName.link)
							.attribute("rel", "stylesheet")
							.attribute("href", "resources/bootstrap/css/bootstrap-theme.min.css"),							
						htmlFactory.tag(TagName.link)
							.attribute("rel", "stylesheet")
							.attribute("href", "resources/font-awesome/css/font-awesome.min.css"),							
						htmlFactory.tag(TagName.link)
							.attribute("rel", "stylesheet")
							.attribute("href", "resources/css/lightbox.css"),							
						htmlFactory.tag(TagName.link)
							.attribute("rel", "stylesheet")
							.attribute("href", "resources/highlight/styles/github.css"),							
						htmlFactory.tag(TagName.link)
							.attribute("rel", "stylesheet")
							.attribute("href", "resources/css/github-markdown.css"),							
						htmlFactory.tag(TagName.link)
							.attribute("rel", "stylesheet")
							.attribute("href", "resources/jstree/themes/default/style.min.css"),
							
						// --- Scripts ---
						htmlFactory.tag(TagName.script).attribute("src", "resources/js/jquery-1.12.1.min.js"),
						htmlFactory.tag(TagName.script).attribute("src", "resources/js/underscore-min.js"),
						htmlFactory.tag(TagName.script).attribute("src", "resources/js/backbone-min.js"),
						htmlFactory.tag(TagName.script).attribute("src", "resources/bootstrap/js/bootstrap.min.js"),
						htmlFactory.tag(TagName.script).attribute("src", "resources/js/d3.min.js"), 				
						htmlFactory.tag(TagName.script).attribute("src", "resources/js/c3.min.js"),												
						htmlFactory.tag(TagName.script).attribute("src", "resources/js/require.js"),
						htmlFactory.tag(TagName.script, htmlFactory.interpolate(getClass().getResource("require-config.js"), "base-url", "resources/js")),
						htmlFactory.tag(TagName.script).attribute("src", "resources/js/lightbox.min.js"),
						htmlFactory.tag(TagName.script).attribute("src", "resources/highlight/highlight.pack.js")), 				
				appPanel);
		
		return app.toString();
	}
	

}
