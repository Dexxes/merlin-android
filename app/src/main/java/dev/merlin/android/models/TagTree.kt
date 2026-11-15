package dev.merlin.android.models

/**
 * Verschachtelte Tags: Baumregeln über der flachen Tag-Liste vom Server ([Tag.parentId]).
 * Portierung von `TagTree.swift` (merlin-ios), Gegenstück zu `TagTree.php`/`tag-tree.js` in
 * merlin-nextcloud. Ein Tag, dessen Eltern-Tag fehlt, gilt als oberste Ebene, damit er nie aus
 * der Liste verschwindet. Kreise in fehlerhaften Daten werden über `seen`-Mengen abgefangen.
 */
class TagTree(val tags: List<Tag>) {

    /** Ein Tag in Baumreihenfolge mit seiner Tiefe (0 = oberste Ebene). */
    data class Row(val tag: Tag, val depth: Int, val hasChildren: Boolean)

    private val byId: Map<Int, Tag> = buildMap { tags.forEach { putIfAbsent(it.id, it) } }

    /** Kinder je Eltern-Id, nach Name sortiert; Schlüssel `null` = oberste Ebene. */
    private val children: Map<Int?, List<Tag>> = tags
        .groupBy { tag -> tag.parentId?.takeIf { it in byId } }
        .mapValues { (_, list) -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }

    fun tag(id: Int): Tag? = byId[id]

    fun hasChildren(id: Int): Boolean = !children[id].isNullOrEmpty()

    /** Ids aller Nachfahren von [id] (ohne [id] selbst). */
    fun descendantIds(id: Int): Set<Int> {
        val result = mutableSetOf<Int>()
        val queue = ArrayDeque(children[id].orEmpty())
        while (queue.isNotEmpty()) {
            val tag = queue.removeFirst()
            if (tag.id == id || !result.add(tag.id)) continue
            queue.addAll(children[tag.id].orEmpty())
        }
        return result
    }

    /** [id] plus alle Nachfahren: was ein Filter auf diesen Tag umfasst. */
    fun scope(id: Int): Set<Int> = descendantIds(id) + id

    /** Ids aller Vorfahren von [id] (Eltern-Tag, dessen Eltern-Tag, …). */
    fun ancestorIds(id: Int): Set<Int> {
        val result = mutableSetOf<Int>()
        var parent = byId[id]?.parentId?.let { byId[it] }
        while (parent != null && parent.id != id && result.add(parent.id)) {
            parent = parent.parentId?.let { byId[it] }
        }
        return result
    }

    /**
     * Auswahl nach Antippen von [id]: Ein ausgewählter Unter-Tag wählt seine Eltern-Tags mit
     * aus, ein abgewählter Tag nimmt seine Unter-Tags mit, damit nie ein Unter-Tag ohne seinen
     * Eltern-Tag ausgewählt bleibt.
     */
    fun toggling(id: Int, selection: Set<Int>): Set<Int> =
        if (id in selection) selection - scope(id) else selecting(id, selection)

    /** [selection] plus [id] und alle seine Eltern-Tags. */
    fun selecting(id: Int, selection: Set<Int>): Set<Int> = selection + ancestorIds(id) + id

    /**
     * Alle Tags in Baumreihenfolge (Eltern vor Kindern, Geschwister nach Name). Mit [collapsed]
     * werden die Kinder dieser Tags ausgelassen.
     */
    fun rows(collapsed: Set<Int> = emptySet()): List<Row> {
        val result = mutableListOf<Row>()
        val seen = mutableSetOf<Int>()
        fun walk(parent: Int?, depth: Int) {
            for (tag in children[parent].orEmpty()) {
                if (!seen.add(tag.id)) continue
                val hasKids = hasChildren(tag.id)
                result += Row(tag, depth, hasKids)
                if (hasKids && tag.id !in collapsed) walk(tag.id, depth + 1)
            }
        }
        walk(null, 0)
        return result
    }

    /** Pfad eines Tags von der obersten Ebene bis zu ihm, z. B. "Reisen › Japan". */
    fun path(tag: Tag): String {
        val names = mutableListOf(tag.name)
        val seen = mutableSetOf(tag.id)
        var parent = tag.parentId?.let { byId[it] }
        while (parent != null && seen.add(parent.id)) {
            names.add(0, parent.name)
            parent = parent.parentId?.let { byId[it] }
        }
        return names.joinToString(" › ")
    }

    /**
     * Darf [id] unter [newParent] hängen? Nicht unter sich selbst und nicht unter einen eigenen
     * Nachfahren; `null` (oberste Ebene) geht immer.
     */
    fun canMove(id: Int, newParent: Int?): Boolean {
        if (newParent == null) return true
        return newParent != id && newParent !in descendantIds(id)
    }
}
