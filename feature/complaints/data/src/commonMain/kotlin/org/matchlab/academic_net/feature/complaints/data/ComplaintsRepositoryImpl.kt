package org.matchlab.academic_net.feature.complaints.data

import org.matchlab.academic_net.feature.complaints.domain.Complaint
import org.matchlab.academic_net.feature.complaints.domain.ComplaintSubject
import org.matchlab.academic_net.feature.complaints.domain.ComplaintTarget
import org.matchlab.academic_net.feature.complaints.domain.ComplaintsRepository

/** [ComplaintsRepository] over a [ComplaintsDataSource]. */
class ComplaintsRepositoryImpl(private val dataSource: ComplaintsDataSource) : ComplaintsRepository {
    override suspend fun getSubject(target: ComplaintTarget): ComplaintSubject? = dataSource.getSubject(target)

    override suspend fun send(complaint: Complaint) = dataSource.send(complaint)
}
