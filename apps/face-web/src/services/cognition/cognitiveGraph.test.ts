import { describe, expect, it } from 'vitest';
import {
  addCognitiveNode, connectCognitiveNodes, emptyCognitiveGraph,
  generalKnowledgeGraph, type CognitiveNode
} from './cognitiveGraph';

const node = (id: string, scope: 'companion-general' | 'owner-private' = 'companion-general',
  ownerId?: string): CognitiveNode => ({
  id, region: 'semantic-memory', label: id, scope, ownerId,
  confidence: 0.8, createdAt: 1
});

describe('local cognitive graph', () => {
  it('creates connected general knowledge across regions', () => {
    const a = addCognitiveNode(emptyCognitiveGraph(), node('cat'));
    const b = addCognitiveNode(a, { ...node('animal'), region: 'perception' });
    const connected = connectCognitiveNodes(b, {
      fromId: 'cat', toId: 'animal', relation: 'is-a', weight: 0.9, updatedAt: 2
    });
    expect(connected.nodes).toHaveLength(2);
    expect(connected.connections).toHaveLength(1);
    expect(a.nodes).toHaveLength(1);
  });

  it('blocks connections between different owners or public and private memories', () => {
    const a = addCognitiveNode(emptyCognitiveGraph(), node('private-a', 'owner-private', 'alice'));
    const b = addCognitiveNode(a, node('private-b', 'owner-private', 'bob'));
    const c = addCognitiveNode(b, node('public'));
    for (const toId of ['private-b', 'public']) {
      expect(() => connectCognitiveNodes(c, {
        fromId: 'private-a', toId, relation: 'related', weight: 0.5, updatedAt: 2
      })).toThrow('privacy boundary');
    }
  });

  it('exports only general knowledge and safe edges', () => {
    let graph = emptyCognitiveGraph();
    graph = addCognitiveNode(graph, node('a'));
    graph = addCognitiveNode(graph, node('b'));
    graph = addCognitiveNode(graph, node('private', 'owner-private', 'alice'));
    graph = connectCognitiveNodes(graph, {
      fromId: 'a', toId: 'b', relation: 'related', weight: 0.5, updatedAt: 2
    });
    const exported = generalKnowledgeGraph(graph);
    expect(exported.nodes.map((item) => item.id)).toEqual(['a', 'b']);
    expect(exported.connections).toHaveLength(1);
    expect(exported.nodes.some((item) => item.ownerId)).toBe(false);
  });

  it('rejects invalid confidence, duplicate nodes and connections', () => {
    const graph = addCognitiveNode(emptyCognitiveGraph(), node('a'));
    expect(() => addCognitiveNode(graph, node('a'))).toThrow();
    expect(() => addCognitiveNode(graph, { ...node('b'), confidence: 2 })).toThrow();
    expect(() => addCognitiveNode(graph, node('private', 'owner-private'))).toThrow();
    const both = addCognitiveNode(graph, node('b'));
    const edge = { fromId: 'a', toId: 'b', relation: 'related', weight: 0.5, updatedAt: 2 };
    expect(() => connectCognitiveNodes(connectCognitiveNodes(both, edge), edge)).toThrow();
  });
});
