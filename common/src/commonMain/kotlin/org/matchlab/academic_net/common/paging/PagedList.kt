package org.matchlab.academic_net.common.paging

/**
 * The pages of one list received so far, joined: what a screen that loads more on scroll shows.
 *
 * The server's list may shift between two requests, so a record can come twice; the repeat is
 * dropped by its key, and a lazy list never gets two items with one key.
 *
 * @property items the records in the order they came
 * @property total the size of the whole list as the server last counted it; never less than [items].size
 * @property next the request for the next page; `null` once the list is over
 */
data class PagedList<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val next: PageRequest? = PageRequest.First,
) {
    /** Whether the server may have more. */
    val hasMore: Boolean get() = next != null

    /** This list followed by the records of [page] whose [key] it does not have yet. */
    fun append(page: Page<T>, key: (T) -> Any): PagedList<T> {
        val known = items.mapTo(HashSet<Any>()) { key(it) }
        val fresh = ArrayList<T>(page.items.size)
        for (item in page.items) {
            if (known.add(key(item))) fresh += item
        }
        val all = items + fresh
        return PagedList(all, maxOf(page.total, all.size), page.next())
    }

    companion object {
        /** The list made of its first [page]; repeats inside the page are dropped too. */
        fun <T> of(page: Page<T>, key: (T) -> Any): PagedList<T> = PagedList<T>().append(page, key)
    }
}
