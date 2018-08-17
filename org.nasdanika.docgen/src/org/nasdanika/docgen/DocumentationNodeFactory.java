package org.nasdanika.docgen;

/**
 * Factory for documentation nodes.
 * @author Pavel Vlasov
 *
 */
public interface DocumentationNodeFactory<T> {
	
	DocumentationNode createDocumentationNode(T obj);

}
