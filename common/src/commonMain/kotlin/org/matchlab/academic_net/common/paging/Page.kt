package org.matchlab.academic_net.common.paging

/**
 * Which part of a long server list to ask for: [limit] records from [offset].
 *
 * The bounds are the Gateway's (`limit` 1..100, `offset` 0..10 000); a request outside them is a
 * programming error, not a server answer, so it fails at once.
 */
data class PageRequest(val offset: Int = 0, val limit: Int = DefaultLimit) {
    init {
        require(offset in 0..MaxOffset) { "offset $offset is out of 0..$MaxOffset" }
        require(limit in 1..MaxLimit) { "limit $limit is out of 1..$MaxLimit" }
    }

    companion object {
        /** The size of a page the app asks for unless a screen needs another. */
        const val DefaultLimit = 20

        /** The largest page the server gives. */
        const val MaxLimit = 100

        /** The largest offset the server accepts. */
        const val MaxOffset = 10_000

        /** The first page of the default size. */
        val First = PageRequest()
    }
}

/**
 * One page of a list the server keeps.
 *
 * @property items the records the app could read, in the server's order
 * @property total the whole list's size as the server counted it when answering
 * @property request what was asked for
 * @property received how many records the server sent, readable or not: the next page starts after
 *  them, so a record the app skipped does not shift the offsets
 */
data class Page<out T>(
    val items: List<T>,
    val total: Int,
    val request: PageRequest,
    val received: Int = items.size,
) {
    init {
        require(total >= 0) { "total $total is negative" }
        require(received >= items.size) { "received $received is less than the ${items.size} items" }
    }

    /** Where the next page starts. */
    val nextOffset: Int get() = request.offset + received

    /**
     * Whether more may be asked for: the server sent something (an empty page ends the list even
     * when [total] says otherwise), [total] counts more than has been received so far, and the next
     * offset is one the server accepts.
     */
    val hasMore: Boolean get() = received > 0 && nextOffset < total && nextOffset <= PageRequest.MaxOffset

    /** The page after this one, of the same size; `null` when [hasMore] is `false`. */
    fun next(): PageRequest? = if (hasMore) PageRequest(nextOffset, request.limit) else null

    /** The same page with every record turned into another by [transform]. */
    fun <R> map(transform: (T) -> R): Page<R> = Page(items.map(transform), total, request, received)
}
