package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.feature.complaints.data.ComplaintsDataSource
import org.matchlab.academic_net.feature.complaints.data.ComplaintsRepositoryImpl
import org.matchlab.academic_net.feature.complaints.domain.ComplaintsRepository
import org.matchlab.academic_net.feature.complaints.domain.GetComplaintSubjectUseCase
import org.matchlab.academic_net.feature.complaints.domain.SendComplaintUseCase
import org.matchlab.academic_net.feature.complaints.ui.ComplaintsUiDependencies

/** The complaints feature: its repository, use cases and what its screens receive. */
internal interface ComplaintsModule {
    @Provides
    @SessionScope
    fun complaintsRepository(source: ComplaintsDataSource): ComplaintsRepository = ComplaintsRepositoryImpl(source)

    @Provides
    fun complaintsUiDependencies(repository: ComplaintsRepository): ComplaintsUiDependencies =
        ComplaintsUiDependencies(
            getComplaintSubject = GetComplaintSubjectUseCase(repository),
            sendComplaint = SendComplaintUseCase(repository),
        )
}
