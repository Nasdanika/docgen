package org.nasdanika.docgen.emf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.dynamichelpers.ExtensionTracker;
import org.eclipse.core.runtime.dynamichelpers.IExtensionChangeHandler;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.nasdanika.codegen.CodegenUtil;
import org.nasdanika.docgen.DocumentationNode;
import org.nasdanika.docgen.DocumentationNodeFactory;
import org.osgi.framework.Bundle;

/**
 * Registry of factories collected from extensions. Creates  
 * @author Pavel Vlasov
 *
 */
public class EObjectDocumentationNodeFactoryRegistry implements DocumentationNodeFactory<EObject>{
	
	public static final EObjectDocumentationNodeFactoryRegistry INSTANCE = new EObjectDocumentationNodeFactoryRegistry();
	
	@Override
	public DocumentationNode createDocumentationNode(EObject obj) {
		if (obj == null) {
			return null;
		}
		Map<DocumentationNodeFactory<EObject>, Integer> matched = match(obj.eClass());
		int distance = -1;
		DocumentationNodeFactory<EObject> matchedFactory = null;
		for (Entry<DocumentationNodeFactory<EObject>, Integer> me: matched.entrySet()) {
			Integer md = me.getValue();
			if (md == 0) {
				return me.getKey().createDocumentationNode(obj);
			}
			if (matchedFactory == null || md < distance) {
				matchedFactory = me.getKey();
			}
		}
		return matchedFactory == null ? new EObjectDocumentationNode(obj): matchedFactory.createDocumentationNode(obj); 
	}
	
	private ExtensionTracker factoryExtensionTracker;
	
	private static final String NAMESPACE_URI_ATTRIBUTE  = "namespace-uri";
	private static final String ECLASS_NAME_ATTRIBUTE  = "eclass-name";
	
	private class FactoryEntry implements Comparable<FactoryEntry> {
		private DocumentationNodeFactory<EObject> factory;
		private String namespaceURI;
		private String eClassName;
		
		FactoryEntry(
				DocumentationNodeFactory<EObject> factory,
				String namespaceURI,
				String eClassName) {
			
			super();
			this.factory = factory;
			this.namespaceURI = namespaceURI;
			this.eClassName = eClassName;
		}
		
		boolean match(EClass eClass) {
			if (CodegenUtil.isBlank(eClassName) || eClass.getName().equals(eClassName)) {
				return eClass.getEPackage().getNsURI().equals(namespaceURI);
			}
			return false;
		}

		/**
		 * Entries without eClassName are evaluated after the ones with one.
		 */
		@Override
		public int compareTo(FactoryEntry o) {
			if (CodegenUtil.isBlank(eClassName)) {
				if (CodegenUtil.isBlank(o.eClassName)) {
					return namespaceURI.compareTo(o.namespaceURI);
				}
				return 1;
			}
			if (CodegenUtil.isBlank(o.eClassName)) {
				return -1; 
			}
						
			return (namespaceURI+"#"+eClassName).compareTo(o.namespaceURI+"#"+o.eClassName);
		}
						
	}
	
	private List<FactoryEntry> factories = new ArrayList<>();
	
	public EObjectDocumentationNodeFactoryRegistry() {
		IExtensionRegistry extensionRegistry = Platform.getExtensionRegistry();
		factoryExtensionTracker = new ExtensionTracker(extensionRegistry);
    	IExtensionPoint extensionPoint = extensionRegistry.getExtensionPoint("org.nasdanika.docgen.emf.documentation-node-factory");   
    	
    	IExtensionChangeHandler extensionChangeHandler = new IExtensionChangeHandler() {

    		@Override
    		public void addExtension(IExtensionTracker tracker, IExtension extension) {
    			for (IConfigurationElement ce: extension.getConfigurationElements()) {
    				try {
	    				if ("documentation-node-factory".equals(ce.getName())) {
	    					IContributor contributor = ce.getContributor();
	    					Bundle bundle = Platform.getBundle(contributor.getName());
	    					Class<?> rc = (Class<?>) bundle.loadClass(ce.getAttribute("factory").trim());
	    					// If factory class is interface, then it must have INSTANCE field.
	    					@SuppressWarnings("unchecked")
							DocumentationNodeFactory<EObject> factory = rc.isInterface() ? (DocumentationNodeFactory<EObject>) rc.getField("INSTANCE").get(null) : (DocumentationNodeFactory<EObject>) ce.createExecutableExtension("factory");
							FactoryEntry factoryEntry = new FactoryEntry(
    								factory,
    								ce.getAttribute(NAMESPACE_URI_ATTRIBUTE),
    								ce.getAttribute(ECLASS_NAME_ATTRIBUTE));
    						
	    					synchronized (factories) {
								factories.add(factoryEntry);
								Collections.sort(factories);
	    					}

	    					tracker.registerObject(extension, factoryEntry, IExtensionTracker.REF_WEAK);
	    				}
    				} catch (Exception e) {    					
    					// TODO - proper logging
    					System.err.println("Error adding factory extension");
    					e.printStackTrace();
    				}
    			}
    		}
    		
 			@Override
    		public void removeExtension(IExtension extension, Object[] objects) {
    			synchronized (factories) {
	    			for (Object obj: objects) {
	    				factories.remove(obj);
	    			}
    			}
			}
    		
    	};    	
    	
		factoryExtensionTracker.registerHandler(extensionChangeHandler, ExtensionTracker.createExtensionPointFilter(extensionPoint));
		for (IExtension ex: extensionPoint.getExtensions()) {
			extensionChangeHandler.addExtension(factoryExtensionTracker, ex);
		}
	}
	
	/**
	 * Collects route entries matching given EClass. 
	 * @param eClass
	 * @return A list of matched route entries. A new modifiable list is returned, so clients can, for example, sort it. 
	 */
	Map<DocumentationNodeFactory<EObject>, Integer> match(EClass eClass) {
		Map<DocumentationNodeFactory<EObject>, Integer> accumulator = new HashMap<>();
		synchronized (factories) {
			match(eClass, accumulator, new HashSet<EClass>(), 0);
		}
		return accumulator;
	}
	
	private void match(EClass eClass, Map<DocumentationNodeFactory<EObject>, Integer> accumulator, HashSet<EClass> traversed, final int distance) {
		if (traversed.add(eClass)) {
			for (FactoryEntry factoryEntry: factories) {
				if (factoryEntry.match(eClass)) {
					if (CodegenUtil.isBlank(factoryEntry.eClassName)) {
						accumulator.put(factoryEntry.factory, distance + 1000000);
					} else {
						accumulator.put(factoryEntry.factory, distance);
						if (distance == 0) {
							return; // Exact match, no need to search further.
						}
					}
				}				
			}
			int offset = 0;
			for (EClass st: eClass.getESuperTypes()) {
				match(st, accumulator, traversed, distance + 1000 + (offset++)); 
			}
		}		
	}	

}
