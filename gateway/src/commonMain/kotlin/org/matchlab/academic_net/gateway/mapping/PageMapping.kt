package org.matchlab.academic_net.gateway.mapping

import org.matchlab.academic_net.common.paging.Page
import org.matchlab.academic_net.common.paging.PageRequest

/**
 * The page the server answered [request] with: [items] are the records the app could read, [received] how many it sent,
 * readable or not. The size of the whole list is [reportedTotal], but never less than what has been received up to the
 * end of this page (`request.offset + received`, counted in Long): a total that is missing (0), negative or too small
 * does not end the list early. A total beyond [Int.MAX_VALUE] is cut to it.
 *
 * @throws IllegalArgumentException if [received] is less than the number of [items]
 */
fun <T> pageOf(items: List<T>, received: Int, reportedTotal: Long, request: PageRequest): Page<T> {
    val receivedUpToHere = request.offset.toLong() + received
    val total = maxOf(reportedTotal, receivedUpToHere).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return Page(items = items, total = total, request = request, received = received)
}
