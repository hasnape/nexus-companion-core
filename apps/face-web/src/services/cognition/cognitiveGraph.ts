/**
 * Local cognitive graph foundation inspired by brain regions.
 * Pure functions; NOT a neural network, biometric model or consciousness.
 * Owner-private memories must be stored separately and never exported by default.
 */
export type BrainRegion =
  | 'perception' | 'attention' | 'working-memory' | 'episodic-memory'
  | 'semantic-memory' | 'procedural-memory' | 'planning'
  | 'language' | 'social-context' | 'safety';

export type KnowledgeScope = 'companion-general' | 'owner-private';

export interface CognitiveNode {
  readonly id: string;
  readonly region: BrainRegion;
  readonly label: string;
  readonly scope: KnowledgeScope;
  readonly ownerId?: string;
  readonly confidence: number;
  readonly createdAt: number;
}

export interface CognitiveConnection {
  readonly fromId: string;
  readonly toId: string;
  readonly relation: string;
  readonly weight: number;
  readonly updatedAt: number;
}

export interface CognitiveGraph {
  readonly nodes: readonly CognitiveNode[];
  readonly connections: readonly CognitiveConnection[];
}

export function emptyCognitiveGraph(): CognitiveGraph {
  return { nodes: [], connections: [] };
}

export function addCognitiveNode(graph: CognitiveGraph, node: CognitiveNode): CognitiveGraph {
  if (!node.id.trim() || !node.label.trim() || !Number.isFinite(node.confidence) ||
      node.confidence < 0 || node.confidence > 1 || !Number.isFinite(node.createdAt) ||
      (node.scope === 'owner-private' && !node.ownerId?.trim()) ||
      (node.scope === 'companion-general' && node.ownerId !== undefined) ||
      graph.nodes.some((existing) => existing.id === node.id)) {
    throw new Error('Invalid or duplicate cognitive node');
  }
  return { nodes: [...graph.nodes, { ...node }], connections: [...graph.connections] };
}

export function connectCognitiveNodes(
  graph: CognitiveGraph,
  connection: CognitiveConnection
): CognitiveGraph {
  const from = graph.nodes.find((node) => node.id === connection.fromId);
  const to = graph.nodes.find((node) => node.id === connection.toId);
  if (!from || !to || from.id === to.id || !connection.relation.trim() ||
      !Number.isFinite(connection.weight) || connection.weight < 0 ||
      connection.weight > 1 || !Number.isFinite(connection.updatedAt) ||
      graph.connections.some((edge) =>
        edge.fromId === connection.fromId && edge.toId === connection.toId &&
        edge.relation === connection.relation)) {
    throw new Error('Invalid or duplicate cognitive connection');
  }
  // A connection to private knowledge is always private to the same owner.
  if (from.scope !== to.scope ||
      (from.scope === 'owner-private' && from.ownerId !== to.ownerId)) {
    throw new Error('Cognitive connection crosses a privacy boundary');
  }
  return { nodes: [...graph.nodes], connections: [...graph.connections, { ...connection }] };
}

/** Public export excludes private nodes and their edges, including indirect references. */
export function generalKnowledgeGraph(graph: CognitiveGraph): CognitiveGraph {
  const nodes = graph.nodes.filter((node) => node.scope === 'companion-general');
  const allowed = new Set(nodes.map((node) => node.id));
  return {
    nodes: nodes.map((node) => ({ ...node })),
    connections: graph.connections.filter((edge) =>
      allowed.has(edge.fromId) && allowed.has(edge.toId)).map((edge) => ({ ...edge }))
  };
}
