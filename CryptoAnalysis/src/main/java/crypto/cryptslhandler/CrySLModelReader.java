package crypto.cryptslhandler;


import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.common.types.JvmExecutable;
import org.eclipse.xtext.common.types.JvmFormalParameter;
import org.eclipse.xtext.common.types.access.impl.ClasspathTypeProvider;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Injector;
import crypto.interfaces.ICryptSLPredicateParameter;
import crypto.interfaces.ISLConstraint;
import crypto.rules.CryptSLArithmeticConstraint;
import crypto.rules.CryptSLArithmeticConstraint.ArithOp;
import crypto.rules.CryptSLComparisonConstraint;
import crypto.rules.CryptSLComparisonConstraint.CompOp;
import crypto.rules.CryptSLCondPredicate;
import crypto.rules.CryptSLConstraint;
import crypto.rules.CryptSLConstraint.LogOps;
import crypto.rules.CryptSLForbiddenMethod;
import crypto.rules.CryptSLMethod;
import crypto.rules.CryptSLObject;
import crypto.rules.CryptSLPredicate;
import crypto.rules.CryptSLRule;
import crypto.rules.CryptSLSplitter;
import crypto.rules.CryptSLValueConstraint;
import crypto.rules.ParEqualsPredicate;
import crypto.rules.StateMachineGraph;
import crypto.rules.StateNode;
import crypto.rules.TransitionEdge;
import de.darmstadt.tu.crossing.CryptSLStandaloneSetup;
import de.darmstadt.tu.crossing.constraints.CrySLArithmeticOperator;
import de.darmstadt.tu.crossing.constraints.CrySLComparisonOperator;
import de.darmstadt.tu.crossing.constraints.CrySLLogicalOperator;
import de.darmstadt.tu.crossing.cryptSL.ArithmeticExpression;
import de.darmstadt.tu.crossing.cryptSL.ArithmeticOperator;
import de.darmstadt.tu.crossing.cryptSL.ArrayElements;
import de.darmstadt.tu.crossing.cryptSL.ComparingOperator;
import de.darmstadt.tu.crossing.cryptSL.ComparisonExpression;
import de.darmstadt.tu.crossing.cryptSL.Constraint;
import de.darmstadt.tu.crossing.cryptSL.DestroysBlock;
import de.darmstadt.tu.crossing.cryptSL.Domainmodel;
import de.darmstadt.tu.crossing.cryptSL.EnsuresBlock;
import de.darmstadt.tu.crossing.cryptSL.Event;
import de.darmstadt.tu.crossing.cryptSL.Expression;
import de.darmstadt.tu.crossing.cryptSL.ForbMethod;
import de.darmstadt.tu.crossing.cryptSL.ForbiddenBlock;
import de.darmstadt.tu.crossing.cryptSL.Literal;
import de.darmstadt.tu.crossing.cryptSL.LiteralExpression;
import de.darmstadt.tu.crossing.cryptSL.LogicalImply;
import de.darmstadt.tu.crossing.cryptSL.LogicalOperator;
import de.darmstadt.tu.crossing.cryptSL.Object;
import de.darmstadt.tu.crossing.cryptSL.ObjectDecl;
import de.darmstadt.tu.crossing.cryptSL.Order;
import de.darmstadt.tu.crossing.cryptSL.PreDefinedPredicates;
import de.darmstadt.tu.crossing.cryptSL.Pred;
import de.darmstadt.tu.crossing.cryptSL.ReqPred;
import de.darmstadt.tu.crossing.cryptSL.ReqPredLit;
import de.darmstadt.tu.crossing.cryptSL.SimpleOrder;
import de.darmstadt.tu.crossing.cryptSL.SuPar;
import de.darmstadt.tu.crossing.cryptSL.SuParList;
import de.darmstadt.tu.crossing.cryptSL.SuperType;
import de.darmstadt.tu.crossing.cryptSL.UnaryPreExpression;
import de.darmstadt.tu.crossing.cryptSL.UseBlock;
import de.darmstadt.tu.crossing.cryptSL.impl.DomainmodelImpl;
import de.darmstadt.tu.crossing.cryptSL.impl.ObjectImpl;


public class CrySLModelReader {
	private List<CryptSLForbiddenMethod> forbiddenMethods = null;
	private StateMachineGraph smg = null;
	private XtextResourceSet resourceSet;
	public static final String cryslFileEnding = ".cryptsl";

	private static final String INT = "int";
	private static final String THIS = "this";
	private static final String ANY_TYPE = "AnyType";
	private static final String NULL = "null";
	private static final String UNDERSCORE = "_";
	
	public CrySLModelReader() throws MalformedURLException {
		CryptSLStandaloneSetup cryptSLStandaloneSetup = new CryptSLStandaloneSetup();
		final Injector injector = cryptSLStandaloneSetup.createInjectorAndDoEMFRegistration();
		this.resourceSet = injector.getInstance(XtextResourceSet.class);

		String a = System.getProperty("java.class.path");
		String[] l = a.split(";");

		URL[] classpath = new URL[l.length];
		for (int i = 0; i < classpath.length; i++) {
			classpath[i] = new File(l[i]).toURI().toURL();
		}

		URLClassLoader ucl = new URLClassLoader(classpath);
		this.resourceSet.setClasspathURIContext(new URLClassLoader(classpath));
		new ClasspathTypeProvider(ucl, this.resourceSet, null, null);
		this.resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);

	}

	public CryptSLRule readRule(File ruleFile) {
		final String fileName = ruleFile.getName();
		final String extension = fileName.substring(fileName.lastIndexOf("."));
		if (!cryslFileEnding.equals(extension)) {
			return null;
		}
		final Resource resource = resourceSet.getResource(URI.createFileURI(ruleFile.getAbsolutePath()), true);// URI.createPlatformResourceURI(ruleFile.getFullPath().toPortableString(),
		// true), true);
		EcoreUtil.resolveAll(resourceSet);
		final EObject eObject = (EObject) resource.getContents().get(0);
		final Domainmodel dm = (Domainmodel) eObject;
		String curClass = dm.getJavaType().getQualifiedName();
		final EnsuresBlock ensure = dm.getEnsure();
		final Map<ParEqualsPredicate, SuperType> pre_preds = Maps.newHashMap();
		final DestroysBlock destroys = dm.getDestroy();
		
		Expression order = dm.getOrder();
		if (order instanceof Order) {
				validateOrder((Order) order);
		}
		if (destroys != null) {
			pre_preds.putAll(getKills(destroys.getPred()));
		}
		if (ensure != null) {
			pre_preds.putAll(getPredicates(ensure.getPred()));
		}

		this.smg = buildStateMachineGraph(order);
		final ForbiddenBlock forbEvent = dm.getForbEvent();
		this.forbiddenMethods = (forbEvent != null) ? getForbiddenMethods(forbEvent.getForb_methods()) : Lists.newArrayList();

		final List<ISLConstraint> constraints = (dm.getReqConstraints() != null) ? buildUpConstraints(dm.getReqConstraints().getReq()) : Lists.newArrayList();
		constraints.addAll(((dm.getRequire() != null) ? collectRequiredPredicates(dm.getRequire().getPred()) : Lists.newArrayList()));
		final List<Entry<String, String>> objects = getObjects(dm.getUsage());

		final List<CryptSLPredicate> actPreds = Lists.newArrayList();

		for (final ParEqualsPredicate pred : pre_preds.keySet()) {
			final SuperType cond = pre_preds.get(pred);
			if (cond == null) {
				actPreds.add(pred.tobasicPredicate());
			} else {
				actPreds.add(new CryptSLCondPredicate(pred.getBaseObject(), pred.getPredName(), pred.getParameters(), pred.isNegated(),
						getStatesForMethods(CryslReaderUtils.resolveAggregateToMethodeNames(cond))));
			}
		}
		return new CryptSLRule(curClass, objects, this.forbiddenMethods, this.smg, constraints, actPreds);
	}
	private void validateOrder(Order order) {
		List<String> collected = new ArrayList<String>();
		collected.addAll(collectLabelsFromExpression(order.getLeft()));
		collected.addAll(collectLabelsFromExpression(order.getRight()));
	}
	
	private List<String> collectLabelsFromExpression(Expression exp) {
		List<String> collected = new ArrayList<String>();
		if (exp instanceof Order || exp instanceof SimpleOrder) {
			collected.addAll(collectLabelsFromExpression(exp.getLeft()));
			collected.addAll(collectLabelsFromExpression(exp.getRight()));
		} else {
			for (Event ev : exp.getOrderEv()) {
				if (ev instanceof SuperType) {
					if (ev instanceof de.darmstadt.tu.crossing.cryptSL.Aggregate) {
						for (Event lab : ((de.darmstadt.tu.crossing.cryptSL.Aggregate) ev).getLab()) {
							if (lab instanceof SuperType) {
								collected.add(((SuperType) lab).getName());
							} else {
								throw new ClassCastException("Parser error in the line after definition of label " + collected.get(collected.size() - 1));
							}
						}
					} else {
						collected.add(((SuperType) ev).getName());
					}
				}
			}
		}
		return collected;
	}
	private Map<? extends ParEqualsPredicate, ? extends SuperType> getKills(final EList<Constraint> eList) {
		final Map<ParEqualsPredicate, SuperType> preds = new HashMap<>();
		for (final Constraint cons : eList) {
			final Pred pred = (Pred) cons;

			final List<ICryptSLPredicateParameter> variables = new ArrayList<>();

			if (pred.getParList() != null) {
				for (final SuPar var : pred.getParList().getParameters()) {
					if (var.getVal() != null) {
						final ObjectImpl object = (ObjectImpl) ((LiteralExpression) var.getVal().getLit().getName()).getValue();
						String name = object.getName();
						String type = ((ObjectDecl) object.eContainer()).getObjectType().getQualifiedName();
						if (name == null) {
							name = THIS;
							type = "";// this.curClass;
						}
						variables.add(new CryptSLObject(name, type));
					} else {
						variables.add(new CryptSLObject(UNDERSCORE, NULL));
					}
				}
			}
			final String meth = pred.getPredName();
			final SuperType cond = pred.getLabelCond();
			if (cond == null) {
				preds.put(new ParEqualsPredicate(null, meth, variables, true), null);
			} else {
				preds.put(new ParEqualsPredicate(null, meth, variables, true), cond);
			}

		}
		return preds;
	}
	private Map<? extends ParEqualsPredicate, ? extends SuperType> getPredicates(final List<Constraint> predList) {
		final Map<ParEqualsPredicate, SuperType> preds = new HashMap<>();
		for (final Constraint cons : predList) {
			final Pred pred = (Pred) cons;
			String curClass = ((DomainmodelImpl) cons.eContainer().eContainer()).getJavaType().getQualifiedName();
			final List<ICryptSLPredicateParameter> variables = new ArrayList<>();

			if (pred.getParList() != null) {
				boolean firstPar = true;
				for (final SuPar var : pred.getParList().getParameters()) {
					if (var.getVal() != null) {
						final ObjectImpl object = (ObjectImpl) ((LiteralExpression) var.getVal().getLit().getName()).getValue();
						String type = ((ObjectDecl) object.eContainer()).getObjectType().getQualifiedName();
						String name = object.getName();
						if (name == null) {
							name = THIS;
							type = curClass;
						}
						variables.add(new CryptSLObject(name, type));
					} else {
						if (firstPar) {
							variables.add(new CryptSLObject(THIS, curClass));
						} else {
							variables.add(new CryptSLObject(UNDERSCORE, NULL));
						}
					}
					firstPar = false;
				}
			}
			final String meth = pred.getPredName();
			final SuperType cond = pred.getLabelCond();
			if (cond == null) {
				preds.put(new ParEqualsPredicate(null, meth, variables, false), null);
			} else {
				preds.put(new ParEqualsPredicate(null, meth, variables, false), cond);
			}

		}
		return preds;
	}
	private List<ISLConstraint> buildUpConstraints(final List<Constraint> constraints) {
		final List<ISLConstraint> slCons = new ArrayList<>();
		for (final Constraint cons : constraints) {
			final ISLConstraint constraint = getConstraint(cons);
			if (constraint != null) {
				slCons.add(constraint);
			}
		}
		return slCons;
	}
	private ISLConstraint getConstraint(final Constraint cons) {
		if (cons == null) {
			return null;
		}
		ISLConstraint slci = null;

		if (cons instanceof ArithmeticExpression) {
			final ArithmeticExpression ae = (ArithmeticExpression) cons;
			String op = new CrySLArithmeticOperator((ArithmeticOperator) ae.getOperator()).toString();
			ArithOp operator = ArithOp.n;
			if ("+".equals(op)) {
				operator = ArithOp.p;
			}
			ObjectDecl leftObj =
					(ObjectDecl) ((ObjectImpl) ((LiteralExpression) ((LiteralExpression) ((LiteralExpression) ae.getLeftExpression()).getCons()).getName()).getValue()).eContainer();
			CryptSLObject leftSide = new CryptSLObject(leftObj.getObjectName().getName(), leftObj.getObjectType().getQualifiedName());

			ObjectDecl rightObj =
					(ObjectDecl) ((ObjectImpl) ((LiteralExpression) ((LiteralExpression) ((LiteralExpression) ae.getRightExpression()).getCons()).getName()).getValue()).eContainer();
			CryptSLObject rightSide = new CryptSLObject(rightObj.getObjectName().getName(), rightObj.getObjectType().getQualifiedName());

			slci = new CryptSLArithmeticConstraint(leftSide, rightSide, operator);
		} else if (cons instanceof LiteralExpression) {
			final LiteralExpression lit = (LiteralExpression) cons;
			final List<String> parList = new ArrayList<>();
			if (lit.getLitsleft() != null) {
				for (final Literal a : lit.getLitsleft().getParameters()) {
					parList.add(filterQuotes(a.getVal()));
				}
			}
			if (lit.getCons() instanceof PreDefinedPredicates) {
				slci = getPredefinedPredicate(lit);
			}else {
				final String part = ((ArrayElements) lit.getCons()).getCons().getPart();
				if (part != null) {
					final LiteralExpression name = (LiteralExpression) ((ArrayElements) lit.getCons()).getCons().getLit().getName();
					final SuperType object = name.getValue();
					final CryptSLObject variable = new CryptSLObject(object.getName(), ((ObjectDecl) object.eContainer()).getObjectType().getQualifiedName(),
					new CryptSLSplitter(Integer.parseInt(((ArrayElements) lit.getCons()).getCons().getInd()), filterQuotes(((ArrayElements) lit.getCons()).getCons().getSplit())));
					slci = new CryptSLValueConstraint(variable, parList);
				} else {
					final String consPred = ((ArrayElements) lit.getCons()).getCons().getConsPred();
					if(consPred != null) {
					final LiteralExpression name = (LiteralExpression) ((ArrayElements) lit.getCons()).getCons().getLit().getName();
					final SuperType object = name.getValue();
					int ind;
					if(consPred.equals("alg(")) {
						ind = 0;
						final CryptSLObject variable = new CryptSLObject(object.getName(), ((ObjectDecl) object.eContainer()).getObjectType().getQualifiedName(),
						new CryptSLSplitter(ind, filterQuotes("/")));
						slci = new CryptSLValueConstraint(variable, parList);
					}else if(consPred.equals("mode(")) {
						ind = 1;
						final CryptSLObject variable = new CryptSLObject(object.getName(), ((ObjectDecl) object.eContainer()).getObjectType().getQualifiedName(),
								new CryptSLSplitter(ind, filterQuotes("/")));
						slci = new CryptSLValueConstraint(variable, parList);
					}else if(consPred.equals("pad(")) {
						ind = 2;
						final CryptSLObject variable = new CryptSLObject(object.getName(), ((ObjectDecl) object.eContainer()).getObjectType().getQualifiedName(),
								new CryptSLSplitter(ind, filterQuotes("/")));
						slci = new CryptSLValueConstraint(variable, parList);
					}
				} else {
					LiteralExpression name = (LiteralExpression) ((ArrayElements) lit.getCons()).getCons().getName();
					if (name == null) {
						name = (LiteralExpression) ((ArrayElements) lit.getCons()).getCons().getLit().getName();
					}
					final SuperType object = name.getValue();
					final CryptSLObject variable = new CryptSLObject(object.getName(), ((ObjectDecl) object.eContainer()).getObjectType().getQualifiedName());
					slci = new CryptSLValueConstraint(variable, parList);
				}
			}
		}
	}else if (cons instanceof ComparisonExpression) {
			final ComparisonExpression comp = (ComparisonExpression) cons;
			CompOp op = null;
			switch ((new CrySLComparisonOperator((ComparingOperator) comp.getOperator())).toString()) {
				case ">":
					op = CompOp.g;
					break;
				case "<":
					op = CompOp.l;
					break;
				case ">=":
					op = CompOp.ge;
					break;
				case "<=":
					op = CompOp.le;
					break;
				case "!=":
					op = CompOp.neq;
					break;
				default:
					op = CompOp.eq;
			}
			CryptSLArithmeticConstraint left;
			CryptSLArithmeticConstraint right;

			final Constraint leftExpression = comp.getLeftExpression();
			if (leftExpression instanceof LiteralExpression) {
				left = convertLiteralToArithmetic(leftExpression);
			} else if (leftExpression instanceof ArithmeticExpression) {
				left = convertArithExpressionToArithmeticConstraint(leftExpression);
			} else {
				left = (CryptSLArithmeticConstraint) leftExpression;
			}

			final Constraint rightExpression = comp.getRightExpression();
			if (rightExpression instanceof LiteralExpression) {
				right = convertLiteralToArithmetic(rightExpression);
			} else {
				right = convertArithExpressionToArithmeticConstraint(rightExpression);
			}
			slci = new CryptSLComparisonConstraint(left, right, op);
		} else if (cons instanceof UnaryPreExpression) {
			final UnaryPreExpression un = (UnaryPreExpression) cons;
			final List<ICryptSLPredicateParameter> vars = new ArrayList<>();
			final Pred innerPredicate = (Pred) un.getEnclosedExpression();
			if (innerPredicate.getParList() != null) {
				for (final SuPar sup : innerPredicate.getParList().getParameters()) {
					vars.add(new CryptSLObject(UNDERSCORE, NULL));
				}
			}
			slci = new CryptSLPredicate(null, innerPredicate.getPredName(), vars, true);
		} else if (cons instanceof Pred) {
			if (((Pred) cons).getPredName() != null && !((Pred) cons).getPredName().isEmpty()) {
				final List<ICryptSLPredicateParameter> vars = new ArrayList<>();

				final SuParList parList = ((Pred) cons).getParList();
				if (parList != null) {
					for (final SuPar sup : parList.getParameters()) {
						vars.add(new CryptSLObject(UNDERSCORE, NULL));
					}
				}
				slci = new CryptSLPredicate(null, ((Pred) cons).getPredName(), vars, false);
			}
		} else if (cons instanceof Constraint) {
			LogOps op = null;
			final EObject operator = cons.getOperator();
			if (operator instanceof LogicalImply) {
				op = LogOps.implies;
			} else {
				switch ((new CrySLLogicalOperator((LogicalOperator) operator)).toString()) {
					case "&&":
						op = LogOps.and;
						break;
					case "||":
						op = LogOps.or;
						break;
					default:
						System.err.println("Sign " + operator.toString() + " was not properly translated.");
						op = LogOps.and;
				}
			}
			slci = new CryptSLConstraint(getConstraint(cons.getLeftExpression()), getConstraint(cons.getRightExpression()), op);
		}

		return slci;
	}
	private List<CryptSLForbiddenMethod> getForbiddenMethods(final EList<ForbMethod> methods) {
		final List<CryptSLForbiddenMethod> methodSignatures = new ArrayList<>();
		for (final ForbMethod fm : methods) {
			final JvmExecutable meth = fm.getJavaMeth();
			final List<Entry<String, String>> pars = new ArrayList<>();
			for (final JvmFormalParameter par : meth.getParameters()) {
				pars.add(new SimpleEntry<>(par.getSimpleName(), par.getParameterType().getSimpleName()));
			}
			final List<CryptSLMethod> crysl = new ArrayList<>();

			final Event alternative = fm.getRep();
			if (alternative != null) {
				crysl.addAll(CryslReaderUtils.resolveAggregateToMethodeNames(alternative));
			}
			methodSignatures.add(new CryptSLForbiddenMethod(
					new CryptSLMethod(meth.getDeclaringType().getIdentifier() + "." + meth.getSimpleName(), pars, null, new SimpleEntry<>(UNDERSCORE, ANY_TYPE)), false, crysl));
		}
		return methodSignatures;
	}
	private List<ISLConstraint> collectRequiredPredicates(final EList<ReqPred> requiredPreds) {
		final List<ISLConstraint> preds = new ArrayList<>();
		for (final ReqPred pred : requiredPreds) {
			ISLConstraint reqPred = null;
			if (pred instanceof ReqPredLit) {
				reqPred = extractReqPred(pred);
			} else {
				final ReqPred left = pred.getLeftExpression();
				final ReqPred right = pred.getRightExpression();
				
				List<CryptSLPredicate> altPreds = retrieveReqPredFromAltPreds(left);
				altPreds.add(extractReqPred(right));
				reqPred = new CryptSLConstraint(altPreds.get(0), altPreds.get(1), LogOps.or);
				for (int i = 2; i < altPreds.size(); i++) {
					reqPred = new CryptSLConstraint(reqPred, altPreds.get(i), LogOps.or);
				}
			}
			preds.add(reqPred);
		}

		return preds;
	}

	private List<CryptSLPredicate> retrieveReqPredFromAltPreds(ReqPred left) {
		List<CryptSLPredicate> preds = new ArrayList<CryptSLPredicate>();
		if (left instanceof ReqPredLit) {
			preds.add(extractReqPred(left));
		} else {
			preds.addAll(retrieveReqPredFromAltPreds(left.getLeftExpression()));
			preds.add(extractReqPred(left.getRightExpression()));
		}
		return preds;
	}
	
	private List<Entry<String, String>> getObjects(final UseBlock usage) {
		final List<Entry<String, String>> objects = new ArrayList<>();

		for (final ObjectDecl obj : usage.getObjects()) {
			objects.add(new SimpleEntry<>(obj.getObjectType().getIdentifier(), obj.getObjectName().getName()));
		}

		return objects;
	}
	private Set<StateNode> getStatesForMethods(final List<CryptSLMethod> condMethods) {
		final Set<StateNode> predGens = new HashSet<>();
		if (condMethods.size() != 0) {
			for (final TransitionEdge methTrans : this.smg.getAllTransitions()) {
				final List<CryptSLMethod> transLabel = methTrans.getLabel();
				if (transLabel.size() > 0 && (transLabel.equals(condMethods) || (condMethods.size() == 1 && transLabel.contains(condMethods.get(0))))) {
					predGens.add(methTrans.getRight());
				}
			}
		}
		return predGens;
	}
	private ISLConstraint getPredefinedPredicate(final LiteralExpression lit) {
		final String pred = ((PreDefinedPredicates) lit.getCons()).getPredName();
		ISLConstraint slci = null;
		switch (pred) {
			case "callTo":
				final List<ICryptSLPredicateParameter> methodsToBeCalled = new ArrayList<>();
				methodsToBeCalled.addAll(CryslReaderUtils.resolveAggregateToMethodeNames(((PreDefinedPredicates) lit.getCons()).getObj().get(0)));
				slci = new CryptSLPredicate(null, pred, methodsToBeCalled, false);
				break;
			case "noCallTo":
				final List<ICryptSLPredicateParameter> methodsNotToBeCalled = new ArrayList<>();
				final List<CryptSLMethod> resolvedMethodNames = CryslReaderUtils.resolveAggregateToMethodeNames(((PreDefinedPredicates) lit.getCons()).getObj().get(0));
				for (final CryptSLMethod csm : resolvedMethodNames) {
					this.forbiddenMethods.add(new CryptSLForbiddenMethod(csm, true));
					methodsNotToBeCalled.add(csm);
				}
				slci = new CryptSLPredicate(null, pred, methodsNotToBeCalled, false);
				break;
			case "neverTypeOf":
				final List<ICryptSLPredicateParameter> varNType = new ArrayList<>();
				final Object object = (de.darmstadt.tu.crossing.cryptSL.Object) ((PreDefinedPredicates) lit.getCons()).getObj().get(0);
				final String type = ((ObjectDecl) object.eContainer()).getObjectType().getQualifiedName();
				varNType.add(new CryptSLObject(object.getName(), type));
				final String qualifiedName = ((PreDefinedPredicates) lit.getCons()).getType().getType().getQualifiedName();
				varNType.add(new CryptSLObject(qualifiedName, NULL));
				slci = new CryptSLPredicate(null, pred, varNType, false);
				break;
			case "length":
				final List<ICryptSLPredicateParameter> variables = new ArrayList<>();
				final Object objectL = (de.darmstadt.tu.crossing.cryptSL.Object) ((PreDefinedPredicates) lit.getCons()).getObj().get(0);
				final String typeL = ((ObjectDecl) objectL.eContainer()).getObjectType().getQualifiedName();
				variables.add(new CryptSLObject(objectL.getName(), typeL));
				slci = new CryptSLPredicate(null, pred, variables, false);
				break;
			case "notHardCoded":
				final List<ICryptSLPredicateParameter> variables1 = new ArrayList<>();
				final Object objectL1 = (de.darmstadt.tu.crossing.cryptSL.Object) ((PreDefinedPredicates) lit.getCons()).getObj().get(0);
				final String typeL1 = ((ObjectDecl) objectL1.eContainer()).getObjectType().getQualifiedName();
				variables1.add(new CryptSLObject(objectL1.getName(), typeL1));
				slci = new CryptSLPredicate(null, pred, variables1, false);
				break;
			case "instanceOf":
				final List<ICryptSLPredicateParameter> varInstOf = new ArrayList<>();
				final Object objInstOf = (de.darmstadt.tu.crossing.cryptSL.Object) ((PreDefinedPredicates) lit.getCons()).getObj().get(0);
				final String instOfType = ((ObjectDecl) objInstOf.eContainer()).getObjectType().getQualifiedName();
				varInstOf.add(new CryptSLObject(objInstOf.getName(), instOfType));
				final String typeName = ((PreDefinedPredicates) lit.getCons()).getType().getType().getQualifiedName();
				varInstOf.add(new CryptSLObject(typeName, NULL));
				slci = new CryptSLPredicate(null, pred, varInstOf, false);
				break;
			default:
				new RuntimeException();
		}
		return slci;
	}
	private CryptSLArithmeticConstraint convertLiteralToArithmetic(final Constraint expression) {
		final LiteralExpression cons = (LiteralExpression) ((LiteralExpression) expression).getCons();
		ICryptSLPredicateParameter name;
		if (cons instanceof PreDefinedPredicates) {
			name = getPredefinedPredicate((LiteralExpression) expression);
		} else {
			final EObject constraint = cons.getName();
			final String object = getValueOfLiteral(constraint);
			if (constraint instanceof LiteralExpression) {
				name = new CryptSLObject(object, ((ObjectDecl) ((ObjectImpl) ((LiteralExpression) constraint).getValue()).eContainer()).getObjectType().getQualifiedName());
			} else {
				name = new CryptSLObject(object, INT);
			}
		}

		return new CryptSLArithmeticConstraint(name, new CryptSLObject("0", INT), crypto.rules.CryptSLArithmeticConstraint.ArithOp.p);
	}
	private CryptSLArithmeticConstraint convertArithExpressionToArithmeticConstraint(final Constraint expression) {
		CryptSLArithmeticConstraint right;
		final ArithmeticExpression ar = (ArithmeticExpression) expression;
		final String leftValue = getValueOfLiteral(ar.getLeftExpression());
		final String rightValue = getValueOfLiteral(ar.getRightExpression());

		final CrySLArithmeticOperator aop = new CrySLArithmeticOperator((ArithmeticOperator) ar.getOperator());
		ArithOp operator = null;
		switch (aop.toString()) {
			case "+":
				operator = ArithOp.p;
				break;
			case "-":
				operator = ArithOp.n;
				break;
			case "%":
				operator = ArithOp.m;
				break;
			default:
				operator = ArithOp.p;
		}
		
		right = new CryptSLArithmeticConstraint(
				new CryptSLObject(leftValue, getTypeName(ar.getLeftExpression(), leftValue)),
				new CryptSLObject(rightValue, getTypeName(ar.getRightExpression(), rightValue)),
				operator);
		return right;
	}
	private CryptSLPredicate extractReqPred(final ReqPred pred) {
		final List<ICryptSLPredicateParameter> variables = new ArrayList<>();
		ReqPredLit innerPred = (ReqPredLit) pred;
		final Constraint conditional = innerPred.getCons();
		if (innerPred.getPred().getParList() != null) {
			for (final SuPar var : innerPred.getPred().getParList().getParameters()) {
				if (var.getVal() != null) {
					final LiteralExpression lit = var.getVal();
					final ObjectImpl object =   (ObjectImpl) ((LiteralExpression) lit.getLit().getName()).getValue();
					final String type = ((ObjectDecl) object.eContainer()).getObjectType().getQualifiedName();
					final String variable = object.getName();
					final String part = var.getVal().getPart();
					if (part != null) {
						variables.add(new CryptSLObject(variable, type, new CryptSLSplitter(Integer.parseInt(lit.getInd()), filterQuotes(lit.getSplit()))));
					}else {
						final String consPred = var.getVal().getConsPred();
						int ind;
						if(consPred != null) {
							if(consPred.equals("alg(")) {
								ind = 0;
								variables.add(new CryptSLObject(variable, type, new CryptSLSplitter(ind, filterQuotes("/"))));
							}else if(consPred.equals("mode(")) {
								ind = 1;
								variables.add(new CryptSLObject(variable, type, new CryptSLSplitter(ind, filterQuotes("/"))));
							}else if(consPred.equals("pad(")) {
								ind = 2;
								variables.add(new CryptSLObject(variable, type, new CryptSLSplitter(ind,filterQuotes ("/"))));
							}
						}else {
							variables.add(new CryptSLObject(variable, type));
						}
					} 
				} else {
					variables.add(new CryptSLObject(UNDERSCORE, NULL));
				}
			}
		}
		return new CryptSLPredicate(null, innerPred.getPred().getPredName(), variables, (innerPred.getNot() != null ? true : false), getConstraint(conditional));
	}
	private String getValueOfLiteral(final EObject name) {
		String value = "";
		if (name instanceof LiteralExpression) {
			final SuperType preValue = ((LiteralExpression) name).getValue();
			if (preValue != null) {
				value = preValue.getName();
			} else {
				final EObject cons = ((LiteralExpression) name).getCons();
				if (cons instanceof LiteralExpression) {
					value = getValueOfLiteral(((LiteralExpression) cons).getName());
				} else {
					value = "";
				}
			}
		} else {
			value = ((Literal) name).getVal();
		}
		return filterQuotes(value);
	}
	private String getTypeName(final Constraint constraint, final String value) {
		String typeName = "";
		try {
			Integer.parseInt(value); 
			typeName = "int";
		} catch (NumberFormatException ex) {
			typeName = ((ObjectDecl) ((LiteralExpression) ((LiteralExpression) ((LiteralExpression) constraint).getCons()).getName()).getValue().eContainer()).getObjectType()
				.getQualifiedName();
		}
		return typeName;
	}


	private StateMachineGraph buildStateMachineGraph(final Expression order) {
		final StateMachineGraphBuilder smgb = new StateMachineGraphBuilder(order);
		return smgb.buildSMG();
	}
	private static String filterQuotes(final String dirty) {
		return CharMatcher.anyOf("\"").removeFrom(dirty);
	}	
}