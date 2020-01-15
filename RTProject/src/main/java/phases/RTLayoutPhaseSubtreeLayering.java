package phases;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.util.IElkProgressMonitor;
import org.eclipse.elk.core.util.Pair;
import org.eclipse.elk.graph.ElkConnectableShape;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.emf.common.util.EList;

import graph.drawing.RTProject.GraphState;
import graph.drawing.RTProject.GraphStatesManager;
import graph.drawing.RTProject.Options;
import helper.Graph;
import helper.Help;

public class RTLayoutPhaseSubtreeLayering implements Phase {
	int gridSize = -1;
	int minSep = 1;
	GraphStatesManager states;
	ElkNode root;
	ElkNode layoutGraph;
	List<List<ElkNode>> layers;

	public RTLayoutPhaseSubtreeLayering(GraphStatesManager states) {
		this.states = states;
	}

	public void apply(ElkNode layoutGraph, IElkProgressMonitor monitor) throws Exception {
		EList<ElkNode> nodes = layoutGraph.getChildren();
		double nodeNodeSpacing = Options.SPACING_NODE_NODE;
		ElkPadding padding = Options.PADDING;
		
		gridSize = (int)nodeNodeSpacing + (int)Math.max(nodes.stream().map(x -> x.getWidth()).max(Double::compare).get(), 
				nodes.stream().map(x -> x.getHeight()).max(Double::compare).get());

		root = nodes.stream().filter(x -> x.getIncomingEdges().size() == 0).findFirst().get();
		this.layoutGraph = layoutGraph;

		layers = new ArrayList<List<ElkNode>>();
		int depth = Help.depth(root);
		for (int i = 0; i < depth; i++)
			layers.add(new ArrayList<ElkNode>());

		for (ElkNode n : nodes)
			layers.get(Help.rootDistance(n, root)).add(n);

		phase1(root);
		states.addState(new GraphState("Phase 1: Done!", Graph.fromElk(layoutGraph)));

		root.setX(-phase2(root) * gridSize + padding.left);
		states.addState(new GraphState("Phase 2: Done!", Graph.fromElk(layoutGraph)));
		
		phase3(root, root.getX(), 0, nodeNodeSpacing, padding);
		states.addState(new GraphState("Phase 3: Done!", Graph.fromElk(layoutGraph)));
	}

	void phase1(ElkNode n) {
		List<ElkNode> childs = Help.getChilds(n);
		for (ElkNode c : childs)
			phase1(c);
		
		final ElkNode leftChild, rightChild;
		leftChild = childs.size() > 0 ? childs.get(0) : null;
		rightChild = childs.size() > 1 ? childs.get(1) : null;

		int dv = minSep * 2;
		if (leftChild != null)
			Help.getProp(leftChild).xOffset = -1;
		if (rightChild != null)
			Help.getProp(rightChild).xOffset = 1;
		
		if (leftChild != null && rightChild != null) {
			Pair<List<ElkNode>, List<ElkNode>> contour = getContourUsingSubtreeLayering(leftChild, rightChild);
			List<ElkNode> leftContour = contour.getFirst(), rightContour = contour.getSecond();
			
			for (int i = 0; i < leftContour.size(); i++) {
				Integer leftSubtreeLayerRightmostTotalX = Help.xOffsetRT(leftContour.get(i), n);
				Integer rightSubtreeLayerLeftmostTotalX = Help.xOffsetRT(rightContour.get(i), n);
				
				if (n.getIdentifier().contentEquals("n1"))
					getClass();
				
				if (leftSubtreeLayerRightmostTotalX + minSep > rightSubtreeLayerLeftmostTotalX - minSep)
					dv = leftSubtreeLayerRightmostTotalX - rightSubtreeLayerLeftmostTotalX + 2 + minSep * 2;
				
				if (-dv / 2 < Help.getProp(leftChild).xOffset)
					Help.getProp(leftChild).xOffset = -dv / 2;
				if (dv / 2 > Help.getProp(rightChild).xOffset)
					Help.getProp(rightChild).xOffset = dv / 2;
				
				states.addState(new GraphState("Phase 1, Postorder: Set offsets of childs of " + n.getIdentifier() + 
						" | Check difference of " + leftContour.get(i).getIdentifier() + 
						" and " + rightContour.get(i).getIdentifier(),
						Graph.fromElk(layoutGraph), n, Help.concat(leftContour, rightContour), 
						leftContour.get(i), rightContour.get(i), 
						leftSubtreeLayerRightmostTotalX, rightSubtreeLayerLeftmostTotalX));
			} 
		} else
			states.addState(new GraphState("Phase 1, Postorder: Visit " + n.getIdentifier(),
					Graph.fromElk(layoutGraph), n));
	}
	Pair<List<ElkNode>, List<ElkNode>> getContourUsingSubtreeLayering(ElkNode leftChild, ElkNode rightChild) {
		Pair<List<ElkNode>, List<ElkNode>> re = new Pair<List<ElkNode>, List<ElkNode>>();
		List<ElkNode> leftContour = new ArrayList<ElkNode>(), rightContour = new ArrayList<ElkNode>();
		re.setFirst(leftContour);
		re.setSecond(rightContour);
		
		List<ElkNode> leftSubtree = Help.getSubtree(leftChild);
		List<ElkNode> rightSubtree = Help.getSubtree(rightChild);
		
		int minDepth = Math.min(Help.depth(rightChild), Help.depth(leftChild));
		for (int i = 0; i < minDepth; i++) {
			final int f = i;
			List<ElkNode> leftSubtreeLayer = leftSubtree.stream().
					filter(x -> Help.rootDistance(x, leftChild) == f).collect(Collectors.toList());
			List<ElkNode> rightSubtreeLayer = rightSubtree.stream().
					filter(x -> Help.rootDistance(x, rightChild) == f).collect(Collectors.toList());
			
			Integer leftSubtreeLayerRightmostNumber = leftSubtreeLayer.stream().
					map(x -> Help.getProp(x).xOffset).max(Double::compare).get();
			ElkNode leftSubtreeLayerRightmost = leftSubtreeLayer.stream().
					filter(x -> Help.getProp(x).xOffset == leftSubtreeLayerRightmostNumber).
					findFirst().get();
			
			Integer rightSubtreeLayerLeftmostNumber = rightSubtreeLayer.stream().
					map(x -> Help.getProp(x).xOffset).min(Double::compare).get();
			ElkNode rightSubtreeLayerLeftmost = rightSubtreeLayer.stream().
					filter(x -> Help.getProp(x).xOffset == rightSubtreeLayerLeftmostNumber).
					findFirst().get();
			
			leftContour.add(leftSubtreeLayerRightmost);
			rightContour.add(rightSubtreeLayerLeftmost);
		} 
		
		return re;
	}

	int phase2(ElkNode r) {
 		int re = 0;
		while (Help.getChilds(r).size() > 0) {
			states.addState(new GraphState("Phase 2, get total X position of the root: " + re,
					Graph.fromElk(layoutGraph), r));
			
			re += Help.getProp(r).xOffset;
			r = Help.getChilds(r).get(0);
		}
		states.addState(new GraphState("Phase 2, total X position of the root: " + re,
				Graph.fromElk(layoutGraph), r));
		return re;
	}

	void phase3(ElkNode r, double rootOffset, int depth, double nodeNodeSpacing, ElkPadding padding) {
		List<ElkNode> childs = Help.getChilds(r);
		
		int offset = Help.getProp(r).xOffset;
		r.setX(offset * gridSize + rootOffset);
		r.setY(Help.rootDistance(r, root) * (r.getHeight() + nodeNodeSpacing) + padding.top);

		states.addState(
				new GraphState("Phase 3, Preorder: Apply offset to " + r.getIdentifier(), 
						Graph.fromElk(layoutGraph), r));

		for (ElkNode c : childs)
			if (c != null)
				phase3(c, r.getX(), depth + 1, nodeNodeSpacing, padding);
	}
}