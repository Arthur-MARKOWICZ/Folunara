package com.arthur.ereader.domain.organization

import com.arthur.ereader.domain.model.OrganizationChildType

data class HierarchyEdge(
    val parentCollectionId: Long,
    val childType: OrganizationChildType,
    val childId: Long,
)

object HierarchyValidator {
    fun validate(edges: Collection<HierarchyEdge>, maxDepth: Int = 8) {
        require(maxDepth > 0)
        val unique = edges.distinct()
        val collectionEdges = unique.filter { it.childType == OrganizationChildType.COLLECTION }
        val adjacency = collectionEdges.groupBy(HierarchyEdge::parentCollectionId)
        val leafParents = unique.filter { it.childType != OrganizationChildType.COLLECTION }
            .map(HierarchyEdge::parentCollectionId).toSet()
        val nodes = collectionEdges.flatMap { listOf(it.parentCollectionId, it.childId) }.toSet() + leafParents
        val state = mutableMapOf<Long, Int>()
        val memo = mutableMapOf<Long, Int>()

        fun depth(node: Long): Int {
            when (state[node]) {
                1 -> error("Coleções não podem formar ciclos.")
                2 -> return memo.getValue(node)
            }
            state[node] = 1
            val collectionChildDepth = adjacency[node].orEmpty().maxOfOrNull { 1 + depth(it.childId) } ?: 1
            val leafDepth = if (node in leafParents) 2 else 1
            val result = maxOf(collectionChildDepth, leafDepth)
            state[node] = 2
            memo[node] = result
            return result
        }

        val depth = nodes.maxOfOrNull(::depth) ?: 0
        require(depth <= maxDepth) { "A hierarquia pode ter no máximo $maxDepth níveis." }
    }
}
