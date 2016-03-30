/*******************************************************************************
 * Copyright (c) 2009, 2016 itemis AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Fabian Steeg    - intial Xtext generation (see bug #277380)
 *     Alexander Nyßen - initial implementation
 *     Tamas Miklossy  - Add support for arrowType edge decorations (bug #477980)
 *
 *******************************************************************************/

package org.eclipse.gef4.dot.internal.parser.validation;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.gef4.common.reflect.ReflectionUtils;
import org.eclipse.gef4.dot.internal.DotAttributes;
import org.eclipse.gef4.dot.internal.DotLanguageSupport;
import org.eclipse.gef4.dot.internal.parser.arrowtype.ArrowtypePackage;
import org.eclipse.gef4.dot.internal.parser.conversion.DotTerminalConverters;
import org.eclipse.gef4.dot.internal.parser.dot.AttrStmt;
import org.eclipse.gef4.dot.internal.parser.dot.Attribute;
import org.eclipse.gef4.dot.internal.parser.dot.AttributeType;
import org.eclipse.gef4.dot.internal.parser.dot.DotGraph;
import org.eclipse.gef4.dot.internal.parser.dot.DotPackage;
import org.eclipse.gef4.dot.internal.parser.dot.EdgeOp;
import org.eclipse.gef4.dot.internal.parser.dot.EdgeRhsNode;
import org.eclipse.gef4.dot.internal.parser.dot.EdgeRhsSubgraph;
import org.eclipse.gef4.dot.internal.parser.dot.EdgeStmtNode;
import org.eclipse.gef4.dot.internal.parser.dot.EdgeStmtSubgraph;
import org.eclipse.gef4.dot.internal.parser.dot.GraphType;
import org.eclipse.gef4.dot.internal.parser.dot.NodeStmt;
import org.eclipse.gef4.dot.internal.parser.dot.Subgraph;
import org.eclipse.gef4.dot.internal.parser.point.PointPackage;
import org.eclipse.gef4.dot.internal.parser.splinetype.SplinetypePackage;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.parser.IParser;
import org.eclipse.xtext.validation.AbstractDeclarativeValidator;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.CheckType;
import org.eclipse.xtext.validation.FeatureBasedDiagnostic;
import org.eclipse.xtext.validation.ValidationMessageAcceptor;

/**
 * Provides DOT-specific validation rules.
 * 
 * @author anyssen
 *
 */
public class DotJavaValidator extends AbstractDotJavaValidator {

	/**
	 * Indication of the context in which an attribute is used.
	 */
	public static enum AttributeContext {
		/**
		 * Graph
		 */
		GRAPH,
		/**
		 * Subgraph/Cluster
		 */
		SUBGRAPH,
		/**
		 * Node
		 */
		NODE,
		/**
		 * Edge
		 */
		EDGE
	}

	/**
	 * Checks that within an {@link Attribute} only valid attribute values are
	 * used (dependent on context, in which the attribute is specified).
	 * 
	 * @param attribute
	 *            The {@link Attribute} to validate.
	 */
	@Check
	public void checkValidAttributeValue(final Attribute attribute) {
		List<Diagnostic> diagnostics = validateAttributeValue(
				getContext(attribute), attribute.getName(),
				attribute.getValue());
		for (Diagnostic d : diagnostics) {
			if (d.getSeverity() == Diagnostic.ERROR) {
				getMessageAcceptor().acceptError(d.getMessage(), attribute,
						DotPackage.Literals.ATTRIBUTE__VALUE,
						INSIGNIFICANT_INDEX, attribute.getName(),
						attribute.getValue());
			} else if (d.getSeverity() == Diagnostic.WARNING) {
				getMessageAcceptor().acceptWarning(d.getMessage(), attribute,
						DotPackage.Literals.ATTRIBUTE__VALUE,
						INSIGNIFICANT_INDEX, attribute.getName(),
						attribute.getValue());
			} else if (d.getSeverity() == Diagnostic.INFO) {
				getMessageAcceptor().acceptInfo(d.getMessage(), attribute,
						DotPackage.Literals.ATTRIBUTE__VALUE,
						INSIGNIFICANT_INDEX, attribute.getName(),
						attribute.getValue());
			}
		}
	}

	private AttributeContext getContext(Attribute attribute) {
		if (isEdgeAttribute(attribute)) {
			return AttributeContext.EDGE;
		} else if (isNodeAttribute(attribute)) {
			return AttributeContext.NODE;
		} else if (isGraphAttribute(attribute)) {
			return AttributeContext.GRAPH;
		} else if (isSubgraphAttribute(attribute)) {
			return AttributeContext.SUBGRAPH;
		} else {
			throw new IllegalArgumentException(
					"Context of attribute could not be determined.");
		}
	}

	/**
	 * Validate the attribute determined via name and value syntactically and
	 * semantically.
	 * 
	 * @param context
	 *            The context element the attribute is related to.
	 * @param name
	 *            The name of the attribute.
	 * @param value
	 *            The value of the attribute (may be quoted).
	 * @return A list of {@link Diagnostic} objects representing the identified
	 *         issues, or an empty list if no issues were found.
	 */
	public List<Diagnostic> validateAttributeValue(
			final AttributeContext context, final String name,
			final String value) {
		// if quoted, we need to unquote the value before parsing it
		String unquotedValue = DotTerminalConverters.unquote(value);

		// use parser (and validator) for respective attribute type
		if (DotAttributes.RANKDIR__G.equals(name)) {
			return validateEnumValue(name, unquotedValue, "rankdir",
					DotAttributes.RANKDIR__G__VALUES);
		} else if (DotAttributes.LAYOUT__G.equals(name)) {
			return validateEnumValue(name, unquotedValue, "layout",
					DotAttributes.LAYOUT__G__VALUES);
		} else if (DotAttributes.DIR__E.equals(name)) {
			return validateEnumValue(name, unquotedValue, "dir",
					DotAttributes.DIR__E__VALUES);
		} else if (DotAttributes.ARROWHEAD__E.equals(name)
				|| DotAttributes.ARROWTAIL__E.equals(name)) {
			// validate arrowtype using delegate parser and validator
			return validateAttributeValue(DotLanguageSupport.ARROWTYPE_PARSER,
					DotLanguageSupport.ARROWTYPE_VALIDATOR, name, unquotedValue,
					ArrowtypePackage.Literals.ARROW_TYPE);
		} else if (DotAttributes.POS__NE.equals(name)) {
			// validate point (node) or splinetype (edge)
			if (AttributeContext.NODE.equals(context)) {
				return validateAttributeValue(DotLanguageSupport.POINT_PARSER,
						DotLanguageSupport.POINT_VALIDATOR, name, unquotedValue,
						PointPackage.Literals.POINT);
			} else if (AttributeContext.EDGE.equals(context)) {
				return validateAttributeValue(
						DotLanguageSupport.SPLINETYPE_PARSER,
						DotLanguageSupport.SPLINETYPE_VALIDATOR, name,
						unquotedValue, SplinetypePackage.Literals.SPLINE_TYPE);
			}
		} else if (DotAttributes.ARROWSIZE__E.equals(name)) {
			return validateDoubleValue(name, unquotedValue, 0.0);
		} else if (DotAttributes.WIDTH__N.equals(name)) {
			return validateDoubleValue(name, unquotedValue, 0.01);
		} else if (DotAttributes.HEIGHT__N.equals(name)) {
			return validateDoubleValue(name, unquotedValue, 0.02);
		} else if (DotAttributes.STYLE__E.equals(name)) {
			if (!DotAttributes.STYLE__E__VALUES.contains(unquotedValue)) {
				return Collections.<Diagnostic> singletonList(
						createSyntacticAttributeValueProblem(unquotedValue,
								"style",
								"Value has to be one of " + getFormattedValues(
										DotAttributes.STYLE__E__VALUES),
								name));
			}
		}
		return Collections.emptyList();
	}

	private List<Diagnostic> validateEnumValue(final String attributeName,
			String attributeValue, String attributeTypeName,
			Set<String> validValues) {
		if (!validValues.contains(attributeValue)) {
			return Collections.<Diagnostic> singletonList(
					createSyntacticAttributeValueProblem(attributeValue,
							attributeTypeName,
							"Value has to be one of "
									+ getFormattedValues(validValues),
							attributeName));
		} else {
			return Collections.emptyList();
		}
	}

	private List<Diagnostic> validateDoubleValue(final String attributeName,
			String attributeValue, double minValue) {
		// parse value
		double parsedValue;
		try {
			parsedValue = Double.parseDouble(attributeValue);
		} catch (NumberFormatException e) {
			return Collections.<Diagnostic> singletonList(
					createSyntacticAttributeValueProblem(attributeValue,
							"double", e.getMessage() + ".", attributeName));
		}
		// validate value
		if (parsedValue < minValue) {
			return Collections.<Diagnostic> singletonList(
					createSemanticAttributeValueProblem(Diagnostic.ERROR,
							attributeValue, "double",
							"Value may not be smaller than " + minValue + ".",
							attributeName));
		}
		return Collections.emptyList();
	}

	private String getFormattedValues(Set<String> values) {
		StringBuilder sb = new StringBuilder();
		for (String value : values) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append("'" + value + "'");
		}
		return sb.append(".").toString();
	}

	private Diagnostic createSyntacticAttributeValueProblem(
			String attributeValue, String attributeTypeName,
			String parserMessage, String issueCode) {
		return new FeatureBasedDiagnostic(Diagnostic.ERROR,
				"The value '" + attributeValue
						+ "' is not a syntactically correct "
						+ attributeTypeName + ": " + parserMessage,
				null /* current object */, DotPackage.Literals.ATTRIBUTE__VALUE,
				INSIGNIFICANT_INDEX, CheckType.NORMAL, issueCode,
				attributeValue);
	}

	private Diagnostic createSemanticAttributeValueProblem(int severity,
			String attributeValue, String attributeTypeName,
			String validatorMessage, String issueCode) {
		return new FeatureBasedDiagnostic(severity,
				"The " + attributeTypeName + " value '" + attributeValue
						+ "' is not semantically correct: " + validatorMessage,
				null /* current object */, DotPackage.Literals.ATTRIBUTE__VALUE,
				INSIGNIFICANT_INDEX, CheckType.NORMAL, issueCode,
				attributeValue);
	}

	private List<Diagnostic> validateAttributeValue(final IParser parser,
			final AbstractDeclarativeValidator validator,
			final String attributeName, final String attributeValue,
			final EClass attributeType) {
		// ensure we always use the unquoted value
		IParseResult parseResult = parser
				.parse(new StringReader(attributeValue));
		if (parseResult.hasSyntaxErrors()) {
			// handle syntactical problems
			return Collections.<Diagnostic> singletonList(
					createSyntacticAttributeValueProblem(attributeValue,
							attributeType.getName().toLowerCase(),
							getFormattedSyntaxErrorMessages(parseResult),
							attributeName));
		} else {
			// handle semantical problems
			final List<Diagnostic> diagnostics = new ArrayList<>();
			// validation is optional; if validator is provided, check for
			// semantic problems using it
			if (validator != null) {
				// we need a specific message acceptor
				validator.setMessageAcceptor(new ValidationMessageAcceptor() {

					@Override
					public void acceptWarning(String message, EObject object,
							int offset, int length, String code,
							String... issueData) {
						diagnostics.add(createSemanticAttributeValueProblem(
								Diagnostic.WARNING, attributeValue,
								attributeType.getName().toLowerCase(), message,
								attributeName));
					}

					@Override
					public void acceptWarning(String message, EObject object,
							EStructuralFeature feature, int index, String code,
							String... issueData) {
						diagnostics.add(createSemanticAttributeValueProblem(
								Diagnostic.WARNING, attributeValue,
								attributeType.getName().toLowerCase(), message,
								attributeName));
					}

					@Override
					public void acceptInfo(String message, EObject object,
							int offset, int length, String code,
							String... issueData) {
						diagnostics.add(createSemanticAttributeValueProblem(
								Diagnostic.INFO, attributeValue,
								attributeType.getName().toLowerCase(), message,
								attributeName));
					}

					@Override
					public void acceptInfo(String message, EObject object,
							EStructuralFeature feature, int index, String code,
							String... issueData) {
						diagnostics.add(createSemanticAttributeValueProblem(
								Diagnostic.INFO, attributeValue,
								attributeType.getName().toLowerCase(), message,
								attributeName));
					}

					@Override
					public void acceptError(String message, EObject object,
							int offset, int length, String code,
							String... issueData) {
						diagnostics.add(createSemanticAttributeValueProblem(
								Diagnostic.ERROR, attributeValue,
								attributeType.getName().toLowerCase(), message,
								attributeName));
					}

					@Override
					public void acceptError(String message, EObject object,
							EStructuralFeature feature, int index, String code,
							String... issueData) {
						diagnostics.add(createSemanticAttributeValueProblem(
								Diagnostic.ERROR, attributeValue,
								attributeType.getName().toLowerCase(), message,
								attributeName));
					}
				});

				Map<Object, Object> context = new HashMap<>();
				context.put(CURRENT_LANGUAGE_NAME, ReflectionUtils
						.getPrivateFieldValue(validator, "languageName"));

				for (Iterator<EObject> iterator = EcoreUtil
						.getAllProperContents(parseResult.getRootASTElement(),
								true); iterator.hasNext();) {
					validator.validate(attributeType, iterator.next(),
							null /* diagnostic chain */, context);
				}
			}
			return diagnostics;
		}
	}

	private static String getFormattedSyntaxErrorMessages(
			IParseResult parseResult) {
		StringBuilder sb = new StringBuilder();
		for (INode n : parseResult.getSyntaxErrors()) {
			String message = n.getSyntaxErrorMessage().getMessage();
			if (!message.isEmpty()) {
				if (sb.length() != 0) {
					sb.append(" ");
				}
				sb.append(message.substring(0, 1).toUpperCase()
						+ message.substring(1) + ".");
			}
		}
		return sb.toString();
	}

	/**
	 * Checks whether the given {@link Attribute} is used in the context of a
	 * node. That is, it is either nested below an {@link NodeStmt} or used
	 * within an {@link AttrStmt} of type {@link AttributeType#NODE}.
	 * 
	 * @param attribute
	 *            The {@link Attribute} to test.
	 * @return <code>true</code> if the {@link Attribute} is used in the context
	 *         of an node, <code>false</code> otherwise.
	 */
	// TODO: move to DotAttributes
	public static boolean isNodeAttribute(Attribute attribute) {
		// attribute nested below EdgeStmtNode or EdgeStmtSubgraph
		if (getAncestorOfType(attribute, NodeStmt.class) != null) {
			return true;
		}
		// global AttrStmt with AttributeType 'node'
		AttrStmt attrStmt = getAncestorOfType(attribute, AttrStmt.class);
		return attrStmt != null
				&& AttributeType.NODE.equals(attrStmt.getType());
	}

	/**
	 * Checks whether the given {@link Attribute} is used in the context of a
	 * subgraph.
	 * 
	 * @param attribute
	 *            The {@link Attribute} to test.
	 * @return <code>true</code> if the {@link Attribute} is used in the context
	 *         of subgraph, <code>false</code> otherwise.
	 */
	// TODO: retrieve AttributeContext instead
	public static boolean isSubgraphAttribute(Attribute attribute) {
		if (isEdgeAttribute(attribute) || isNodeAttribute(attribute)) {
			return false;
		}
		// attribute nested below Subgraph
		return getAncestorOfType(attribute, Subgraph.class) != null;
	}

	/**
	 * Checks whether the given {@link Attribute} is used in the context of a
	 * top-level graph.
	 * 
	 * @param attribute
	 *            The {@link Attribute} to test.
	 * @return <code>true</code> if the {@link Attribute} is used in the context
	 *         of a top-level graph, <code>false</code> otherwise.
	 */
	// TODO: move to DotAttributes
	public static boolean isGraphAttribute(Attribute attribute) {
		// attribute nested below EdgeStmtNode or EdgeStmtSubgraph
		if (isEdgeAttribute(attribute) || isNodeAttribute(attribute)
				|| isSubgraphAttribute(attribute)) {
			return false;
		}
		return true;
	}

	/**
	 * Checks whether the given {@link Attribute} is used in the context of an
	 * edge. That is, it is either nested below an {@link EdgeStmtNode} or an
	 * {@link EdgeStmtSubgraph}, or used within an {@link AttrStmt} of type
	 * {@link AttributeType#EDGE}.
	 * 
	 * @param attribute
	 *            The {@link Attribute} to test.
	 * @return <code>true</code> if the {@link Attribute} is used in the context
	 *         of an edge, <code>false</code> otherwise.
	 */
	// TODO: retrieve attribute context instead
	public static boolean isEdgeAttribute(Attribute attribute) {
		// attribute nested below EdgeStmtNode or EdgeStmtSubgraph
		if (getAncestorOfType(attribute, EdgeStmtNode.class) != null
				|| getAncestorOfType(attribute,
						EdgeStmtSubgraph.class) != null) {
			return true;
		}
		// global AttrStmt with AttributeType 'edge'
		AttrStmt attrStmt = getAncestorOfType(attribute, AttrStmt.class);
		return attrStmt != null
				&& AttributeType.EDGE.equals(attrStmt.getType());
	}

	/**
	 * Ensures that within {@link EdgeRhsNode}, '-&gt;' is used in directed
	 * graphs, while '--' is used in undirected graphs.
	 * 
	 * @param edgeRhsNode
	 *            The EdgeRhsNode to validate.
	 */
	@Check
	public void checkEdgeOpCorrespondsToGraphType(EdgeRhsNode edgeRhsNode) {
		checkEdgeOpCorrespondsToGraphType(edgeRhsNode.getOp(),
				getAncestorOfType(edgeRhsNode, DotGraph.class).getType());
	}

	/**
	 * Ensures that within {@link EdgeRhsSubgraph} '-&gt;' is used in directed
	 * graphs, while '--' is used in undirected graphs.
	 * 
	 * @param edgeRhsSubgraph
	 *            The EdgeRhsSubgraph to validate.
	 */
	@Check
	public void checkEdgeOpCorrespondsToGraphType(
			EdgeRhsSubgraph edgeRhsSubgraph) {
		checkEdgeOpCorrespondsToGraphType(edgeRhsSubgraph.getOp(),
				getAncestorOfType(edgeRhsSubgraph, DotGraph.class).getType());
	}

	private void checkEdgeOpCorrespondsToGraphType(EdgeOp edgeOp,
			GraphType graphType) {
		boolean edgeDirected = edgeOp.equals(EdgeOp.DIRECTED);
		boolean graphDirected = graphType.equals(GraphType.DIGRAPH);
		if (graphDirected && !edgeDirected) {
			error("EdgeOp '--' may only be used in undirected graphs.",
					DotPackage.eINSTANCE.getEdgeRhs_Op());

		} else if (!graphDirected && edgeDirected) {
			error("EdgeOp '->' may only be used in directed graphs.",
					DotPackage.eINSTANCE.getEdgeRhs_Op());
		}
	}

	@SuppressWarnings("unchecked")
	private static <T extends EObject> T getAncestorOfType(EObject eObject,
			Class<T> type) {
		EObject container = eObject.eContainer();
		while (container != null
				&& !type.isAssignableFrom(container.getClass())) {
			container = container.eContainer();
		}
		return (T) container;
	}

}
