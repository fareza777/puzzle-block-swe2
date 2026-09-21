package com.fareza.blokku.core

/**
 * Campaign map — a fixed zig-zag path of nodes, each a small challenge built
 * on an existing mode. Completing node i unlocks i+1. Progress persists.
 */
object Campaign {

    enum class NodeType { PUZZLE, LEVEL, RUSH, BOSS, MOSAIC, MERGE, AVALANCHE, GRAVITY, GAMBIT, EXPEDITION, VERSUS }

    class Node(val type: NodeType, val arg: Int, val label: String)

    val NODES: List<Node> = listOf(
        Node(NodeType.PUZZLE,     0, "First Steps"),
        Node(NodeType.LEVEL,      2, "Clearing Crew"),
        Node(NodeType.RUSH,     700, "Sprint"),
        Node(NodeType.MOSAIC,     0, "Canvas"),
        Node(NodeType.PUZZLE,     6, "Tight Fit"),
        Node(NodeType.BOSS,       2, "Stone Golem"),
        Node(NodeType.MERGE,   1500, "Doubler"),
        Node(NodeType.LEVEL,      8, "Assembly"),
        Node(NodeType.PUZZLE,    12, "Gap Hunter"),
        Node(NodeType.AVALANCHE, 900, "Landslide"),
        Node(NodeType.GRAVITY, 1200, "Freefall"),
        Node(NodeType.MOSAIC,     2, "Whiskers"),
        Node(NodeType.BOSS,       3, "Boulder King"),
        Node(NodeType.GAMBIT,  1600, "High Stakes"),
        Node(NodeType.LEVEL,     14, "Clockwork"),
        Node(NodeType.EXPEDITION,2500, "Expedition"),
        Node(NodeType.PUZZLE,    20, "Surgeon"),
        Node(NodeType.VERSUS,     0, "Rival"),
        Node(NodeType.MOSAIC,     4, "Deep Sea"),
        Node(NodeType.BOSS,       4, "Obsidian"),
    )

    val COUNT get() = NODES.size

    /** The engine a node launches. Target nodes get a SCORE goal so they end. */
    fun buildEngine(i: Int): GameEngine {
        val n = NODES[i.coerceIn(0, COUNT - 1)]
        return when (n.type) {
            NodeType.PUZZLE -> GameEngine.puzzle(Puzzles.get(n.arg))
            NodeType.LEVEL -> GameEngine.level(Levels.get(n.arg))
            NodeType.RUSH -> {
                val e = GameEngine.rush()
                e.goal = Goal(GoalType.SCORE, n.arg)
                e
            }
            NodeType.BOSS -> GameEngine.boss(n.arg)
            NodeType.MOSAIC -> GameEngine.mosaic(n.arg)
            NodeType.MERGE -> scoreTarget(GameEngine.merge(), n.arg)
            NodeType.AVALANCHE -> scoreTarget(GameEngine.avalanche(), n.arg)
            NodeType.GRAVITY -> scoreTarget(GameEngine.gravity(), n.arg)
            NodeType.GAMBIT -> scoreTarget(GameEngine.gambit(), n.arg)
            NodeType.EXPEDITION -> scoreTarget(GameEngine.expedition(), n.arg)
            NodeType.VERSUS -> GameEngine.versus()
        }
    }

    private fun scoreTarget(e: GameEngine, target: Int): GameEngine {
        e.goal = Goal(GoalType.SCORE, target)
        return e
    }

    /** Node path coordinates for the map scene — zig-zag columns, bottom-up. */
    fun nodePos(i: Int): Pair<Float, Float> {
        // x in 0..1 across the path area; y ascending so node 0 sits at the bottom
        val col = i % 4 // zig-zag: 0,1,2,3,3,2,1,0...
        val zig = intArrayOf(0, 1, 2, 3, 3, 2, 1, 0)
        val x = 0.14f + zig[i % 8] * 0.24f
        val y = i * 0.9f
        return x to y
    }
}
